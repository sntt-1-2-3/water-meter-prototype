package com.zhishuiyun.water.service;

import com.zhishuiyun.water.entity.AuditLog;
import com.zhishuiyun.water.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 审计日志：只增不改。
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(String operator, String role, String action, String target,
                    String requestId, String result, String oldValue, String newValue) {
        auditLogRepository.save(AuditLog.builder()
                .operator(operator)
                .role(role)
                .action(action)
                .target(target)
                .requestId(requestId)
                .result(result)
                .oldValue(oldValue)
                .newValue(newValue)
                .ts(LocalDateTime.now())
                .build());
    }
}
