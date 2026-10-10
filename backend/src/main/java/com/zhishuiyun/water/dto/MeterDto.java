package com.zhishuiyun.water.dto;

import com.zhishuiyun.water.entity.Meter;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * 水表视图对象，字段名对齐前端 water.ts 的 Meter 接口。
 */
public record MeterDto(
        String id,
        String user,
        String area,
        String type,
        BigDecimal reading,
        String status,
        String updated) {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static MeterDto from(Meter m) {
        return new MeterDto(
                m.getMeterId(),
                m.getUserName(),
                m.getArea(),
                m.getType() != null ? m.getType().name() : null,
                m.getReading(),
                m.getStatus() != null ? m.getStatus().name() : null,
                m.getUpdatedAt() != null ? m.getUpdatedAt().format(FMT) : null);
    }
}
