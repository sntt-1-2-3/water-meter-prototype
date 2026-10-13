package com.zhishuiyun.water.dto;

import com.zhishuiyun.water.entity.ReadingTask;

import java.time.format.DateTimeFormatter;

/**
 * 抄表任务视图，对齐前端 ReadingTask 接口。
 */
public record ReadingTaskDto(
        Long id,
        String name,
        String scope,
        Integer progress,
        String status,
        String time,
        Integer total,
        Integer success,
        Integer missed,
        Integer failed,
        Integer retryCount,
        java.time.LocalDateTime endTime) {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    public static ReadingTaskDto from(ReadingTask t) {
        return new ReadingTaskDto(
                t.getId(),
                t.getName(),
                t.getScope(),
                t.getProgress(),
                t.getStatus() != null ? t.getStatus().name() : null,
                t.getStartTime() != null ? t.getStartTime().format(FMT)
                        : (t.getPlanTime() != null ? t.getPlanTime().format(FMT) : null),
                t.getTotal(), t.getSuccess(), t.getMissed(), t.getFailed(), t.getRetryCount(), t.getEndTime());
    }
}
