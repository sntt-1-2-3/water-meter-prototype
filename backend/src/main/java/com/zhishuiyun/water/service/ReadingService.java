package com.zhishuiyun.water.service;

import com.zhishuiyun.water.common.RequestContext;
import com.zhishuiyun.water.dto.ReadingTaskDto;
import com.zhishuiyun.water.entity.DomainEnums;
import com.zhishuiyun.water.entity.Meter;
import com.zhishuiyun.water.entity.ReadingTask;
import com.zhishuiyun.water.repository.MeterRepository;
import com.zhishuiyun.water.repository.ReadingRepository;
import com.zhishuiyun.water.repository.ReadingTaskRepository;
import com.zhishuiyun.water.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * 抄表任务：同周期幂等，统计成功/漏抄，模拟补抄与失败。
 */
@Service
@RequiredArgsConstructor
public class ReadingService {

    private final ReadingTaskRepository taskRepository;
    private final MeterRepository meterRepository;
    private final ReadingRepository readingRepository;
    private final AuditService auditService;

    @Transactional
    public ReadingTaskDto startReading(AuthUser user) {
        String period = YearMonth.now().toString();
        // 幂等：同周期已有未完成/未失败的任务则直接返回
        ReadingTask existing = taskRepository
                .findFirstByPeriodAndStatusNot(period, DomainEnums.TaskStatus.已完成).orElse(null);
        if (existing != null && existing.getStatus() != DomainEnums.TaskStatus.失败待重试) {
            return ReadingTaskDto.from(existing);
        }

        List<Meter> meters = meterRepository.findAll();
        int total = meters.size();
        int success = 0;
        int missed = 0;
        for (Meter m : meters) {
            boolean hasReading = readingRepository.findFirstByMeterIdOrderByTsDesc(m.getMeterId()).isPresent();
            if (hasReading) {
                success++;
            } else {
                missed++;
            }
        }

        ReadingTask task = ReadingTask.builder()
                .name(period + " 日常自动抄表")
                .scope("全部区域 · " + total + " 台")
                .period(period)
                .total(total)
                .success(success)
                .missed(missed)
                .failed(0)
                .retryCount(0)
                .status(DomainEnums.TaskStatus.已完成)
                .progress(100)
                .planTime(LocalDateTime.now())
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .build();
        taskRepository.save(task);
        auditService.log(user.getUsername(), user.getRole().name(), "START_READING", period,
                RequestContext.getRequestId(), "SUCCESS", null, "total=" + total + ",success=" + success);
        return ReadingTaskDto.from(task);
    }

    @Transactional(readOnly = true)
    public List<ReadingTaskDto> listTasks() {
        return taskRepository.findAllByOrderByIdDesc().stream().map(ReadingTaskDto::from).toList();
    }
}
