package com.zhishuiyun.water.dto;

import java.math.BigDecimal;
import java.util.List;

public record UserSummaryDto(String name, String meterId, String address, BigDecimal usage, BigDecimal amount,
                             BigDecimal paid, BigDecimal outstanding, String status, BigDecimal reading,
                             List<TrendPoint> trend) {
    public record TrendPoint(String date, BigDecimal usage) { }
}
