package com.zhishuiyun.water.dto;

import java.math.BigDecimal;

/**
 * 计费结果：水费、违约金、应缴（均为分精度，HALF_UP 四舍五入）。
 */
public record FeeResult(BigDecimal waterFee, BigDecimal penalty, BigDecimal totalDue) {
}
