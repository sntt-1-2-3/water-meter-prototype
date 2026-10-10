package com.zhishuiyun.water.dto;

import java.math.BigDecimal;

/**
 * 原始上报数据请求。字段允许为 null，由 ReportService 做数据质量（DQ）校验
 * 并返回精确错误码。
 */
public record ReportRequest(
        String meterId,
        String ts,
        BigDecimal instantFlow,
        BigDecimal cumulativeFlow,
        BigDecimal temperature,
        String valve,
        String alarmCode) {
}
