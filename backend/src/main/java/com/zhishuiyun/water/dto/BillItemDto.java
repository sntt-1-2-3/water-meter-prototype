package com.zhishuiyun.water.dto;
import com.zhishuiyun.water.entity.BillItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
public record BillItemDto(Long id, String batch, String period, String meterId, String userName,
                          String area, String meterType, BigDecimal usage, BigDecimal openingReading,
                          BigDecimal closingReading, BigDecimal waterFee, BigDecimal penalty,
                          BigDecimal totalDue, BigDecimal paidAmount, BigDecimal outstanding,
                          Integer overdueDays, String status, LocalDate dueDate, LocalDateTime paidAt) {
    public static BillItemDto from(BillItem item) {
        BigDecimal paid = item.getPaidAmount() == null ? BigDecimal.ZERO : item.getPaidAmount();
        return new BillItemDto(item.getId(), item.getBatchNo(), item.getPeriod(), item.getMeterId(),
                item.getUserName(), item.getArea(), item.getMeterType().name(), item.getUsage(),
                item.getOpeningReading(), item.getClosingReading(), item.getWaterFee(), item.getPenalty(),
                item.getTotalDue(), paid, item.getTotalDue().subtract(paid).max(BigDecimal.ZERO),
                item.getOverdueDays(), item.getStatus().name(), item.getDueDate(), item.getPaidAt());
    }
}
