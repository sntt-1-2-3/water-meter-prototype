package com.zhishuiyun.water.dto;
import com.zhishuiyun.water.entity.Payment;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record PaymentDto(Long id, String transactionNo, Long billItemId, String meterId,
                         BigDecimal amount, LocalDateTime paidAt, String payer, String channel,
                         String idempotencyKey) {
    public static PaymentDto from(Payment p) {
        return new PaymentDto(p.getId(), p.getTransactionNo(), p.getBillItemId(), p.getMeterId(),
                p.getAmount(), p.getPaidAt(), p.getPayer(), p.getChannel(), p.getIdempotencyKey());
    }
}
