package com.zhishuiyun.water.dto;

import com.zhishuiyun.water.entity.Notification;
import java.time.format.DateTimeFormatter;

public record NotificationDto(Long id, String title, String content, String time, String route,
                              boolean read, String readAt, String type) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static NotificationDto from(Notification message) {
        return new NotificationDto(message.getId(), message.getTitle(), message.getContent(),
                message.getCreatedAt().format(FMT), message.getRoute(), message.getReadAt() != null,
                message.getReadAt() == null ? null : message.getReadAt().format(FMT), "system");
    }
}
