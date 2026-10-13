package com.zhishuiyun.water.service;

import com.zhishuiyun.water.common.BusinessException;
import com.zhishuiyun.water.common.ErrorCode;
import com.zhishuiyun.water.common.RequestContext;
import com.zhishuiyun.water.dto.FeedbackDto;
import com.zhishuiyun.water.dto.FeedbackRequest;
import com.zhishuiyun.water.dto.FeedbackUpdateRequest;
import com.zhishuiyun.water.entity.DomainEnums.AccountRole;
import com.zhishuiyun.water.entity.Meter;
import com.zhishuiyun.water.entity.UserFeedback;
import com.zhishuiyun.water.repository.MeterRepository;
import com.zhishuiyun.water.repository.UserFeedbackRepository;
import com.zhishuiyun.water.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedbackService {
    private final UserFeedbackRepository feedbackRepository;
    private final MeterRepository meterRepository;
    private final AccessPolicy accessPolicy;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<FeedbackDto> list(AuthUser user) {
        if (user.getRole() == AccountRole.FINANCE) throw new BusinessException(ErrorCode.FORBIDDEN);
        return feedbackRepository.findAllByOrderByIdDesc().stream()
                .filter(feedback -> user.isUser() ? user.getUsername().equals(feedback.getUsername())
                        : accessPolicy.canAccessArea(user, feedback.getArea()))
                .map(FeedbackDto::from).toList();
    }

    @Transactional
    public FeedbackDto submit(FeedbackRequest request, AuthUser user) {
        if (!user.isUser()) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (user.getBoundMeterId() == null || user.getBoundMeterId().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先联系管理员绑定水表，再提交反馈");
        }
        Meter meter = meterRepository.findByMeterId(user.getBoundMeterId())
                .orElseThrow(() -> new BusinessException(ErrorCode.METER_NOT_FOUND));
        accessPolicy.requireAccessMeter(user, meter);
        LocalDateTime now = LocalDateTime.now();
        UserFeedback feedback = feedbackRepository.save(UserFeedback.builder().username(user.getUsername())
                .displayName(user.getName()).meterId(meter.getMeterId()).area(meter.getArea()).type(request.type().trim())
                .description(request.description().trim()).status("待处理").createdAt(now).updatedAt(now).build());
        notificationService.notifyAdministrators("收到用户反馈 #" + feedback.getId(),
                user.getName() + "（" + meter.getMeterId() + "）提交了" + feedback.getType() + "反馈，请及时处理。", "/admin/feedback");
        notificationService.notifyUser(user.getUsername(), "反馈已提交 #" + feedback.getId(),
                "您的" + feedback.getType() + "反馈已保存，处理结果会通过通知告知。", "/user/home");
        auditService.log(user.getUsername(), user.getRole().name(), "CREATE_FEEDBACK", String.valueOf(feedback.getId()),
                RequestContext.getRequestId(), "SUCCESS", null, "待处理");
        return FeedbackDto.from(feedback);
    }

    @Transactional
    public FeedbackDto update(Long id, FeedbackUpdateRequest request, AuthUser user) {
        accessPolicy.requireManageAnomalies(user);
        UserFeedback feedback = feedbackRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!accessPolicy.canAccessArea(user, feedback.getArea())) throw new BusinessException(ErrorCode.FORBIDDEN);
        if ("已完成".equals(feedback.getStatus())) throw new BusinessException(ErrorCode.BAD_REQUEST, "已完成的反馈不能再次修改");
        String resolution = request.resolution() == null ? "" : request.resolution().trim();
        if ("已完成".equals(request.status()) && resolution.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "完成处理时必须填写处理结果");
        }
        String oldStatus = feedback.getStatus();
        feedback.setStatus(request.status());
        feedback.setResolution(resolution);
        feedback.setHandledBy(user.getUsername());
        feedback.setUpdatedAt(LocalDateTime.now());
        feedbackRepository.save(feedback);
        notificationService.notifyUser(feedback.getUsername(), "反馈 #" + id + " " + request.status(),
                resolution.isBlank() ? "工作人员正在处理您的反馈。" : "处理结果：" + resolution, "/user/home");
        auditService.log(user.getUsername(), user.getRole().name(), "UPDATE_FEEDBACK", String.valueOf(id),
                RequestContext.getRequestId(), "SUCCESS", oldStatus, request.status());
        return FeedbackDto.from(feedback);
    }
}
