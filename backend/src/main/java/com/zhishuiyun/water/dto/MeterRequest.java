package com.zhishuiyun.water.dto;

import com.zhishuiyun.water.entity.DomainEnums;

/**
 * 新增/编辑水表请求。
 */
public record MeterRequest(
        String meterId,
        String userName,
        String area,
        DomainEnums.MeterType type,
        java.math.BigDecimal reading,
        java.math.BigDecimal lastReading,
        DomainEnums.MeterStatus status,
        java.time.LocalDateTime installTime) {
}
