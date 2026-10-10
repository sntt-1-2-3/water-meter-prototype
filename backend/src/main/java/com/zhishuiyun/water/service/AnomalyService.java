package com.zhishuiyun.water.service;

import com.zhishuiyun.water.common.BusinessException;
import com.zhishuiyun.water.common.ErrorCode;
import com.zhishuiyun.water.common.RequestContext;
import com.zhishuiyun.water.dto.AnomalyDto;
import com.zhishuiyun.water.entity.Anomaly;
import com.zhishuiyun.water.entity.DomainEnums;
import com.zhishuiyun.water.repository.AnomalyRepository;
import com.zhishuiyun.water.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异常中心：风险筛选、状态机流转、数据范围过滤。
 */
@Service
@RequiredArgsConstructor
public class AnomalyService {

    private final AnomalyRepository anomalyRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<AnomalyDto> list(String level, AuthUser user) {
        if (user.isUser()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        List<Anomaly> all;
        if (level != null && !level.isBlank() && !"全部".equals(level)) {
            all = anomalyRepository.findByLevel(DomainEnums.RiskLevel.valueOf(level));
        } else {
            all = anomalyRepository.findAllByOrderByIdDesc();
        }
        return all.stream()
                .filter(a -> user.canAccessArea(a.getArea()))
                .map(AnomalyDto::from)
                .toList();
    }

    @Transactional
    public AnomalyDto updateStatus(String anomalyId, String status, AuthUser user) {
        if (user.isAuditor()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Anomaly anomaly = anomalyRepository.findAll().stream()
                .filter(a -> a.getAnomalyId().equals(anomalyId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!user.canAccessArea(anomaly.getArea())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        DomainEnums.AnomalyStatus target = DomainEnums.AnomalyStatus.valueOf(status);
        if (!isValidTransition(anomaly.getStatus(), target)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不允许从" + anomaly.getStatus().name()
                    + "直接流转到" + target.name());
        }
        anomaly.setStatus(target);
        anomaly.setConfirmedBy(user.getUsername());
        anomaly.setConfirmedAt(LocalDateTime.now());
        Anomaly saved = anomalyRepository.save(anomaly);
        auditService.log(user.getUsername(), user.getRole().name(), "UPDATE_ANOMALY", anomalyId,
                RequestContext.getRequestId(), "SUCCESS", anomaly.getStatus().name(), target.name());
        return AnomalyDto.from(saved);
    }

    private boolean isValidTransition(DomainEnums.AnomalyStatus from, DomainEnums.AnomalyStatus to) {
        return (from == DomainEnums.AnomalyStatus.待处理 && to == DomainEnums.AnomalyStatus.处理中)
                || (from == DomainEnums.AnomalyStatus.处理中 && to == DomainEnums.AnomalyStatus.已完成);
    }
}
