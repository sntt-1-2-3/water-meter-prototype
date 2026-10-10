package com.zhishuiyun.water.dto;

import com.zhishuiyun.water.entity.Anomaly;

import java.time.format.DateTimeFormatter;

/**
 * 异常视图，对齐前端 Anomaly 接口。
 */
public record AnomalyDto(
        String id,
        String meter,
        String area,
        String type,
        String level,
        String time,
        String status) {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static AnomalyDto from(Anomaly a) {
        return new AnomalyDto(
                a.getAnomalyId(),
                a.getMeterId(),
                a.getArea(),
                a.getType(),
                a.getLevel() != null ? a.getLevel().name() : null,
                a.getTime() != null ? a.getTime().format(FMT) : null,
                a.getStatus() != null ? a.getStatus().name() : null);
    }
}
