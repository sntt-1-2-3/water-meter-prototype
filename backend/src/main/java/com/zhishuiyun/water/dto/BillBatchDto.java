package com.zhishuiyun.water.dto;

import com.zhishuiyun.water.entity.Bill;

import java.math.BigDecimal;

/**
 * 账单批次视图，对齐前端 BillBatch 接口。
 */
public record BillBatchDto(
        String batch,
        Integer users,
        BigDecimal amount,
        String paid,
        String status) {

    public static BillBatchDto from(Bill b) {
        String paidStr = "0%";
        if (b.getTotalAmount() != null && b.getTotalAmount().signum() > 0 && b.getTotalPaid() != null) {
            paidStr = b.getTotalPaid()
                    .multiply(new BigDecimal("100"))
                    .divide(b.getTotalAmount(), 1, java.math.RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString() + "%";
        }
        return new BillBatchDto(
                b.getBatchNo(),
                b.getUserCount(),
                b.getTotalAmount(),
                paidStr,
                b.getStatus());
    }
}
