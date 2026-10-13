package com.zhishuiyun.water.dto;

import com.zhishuiyun.water.entity.UserFeedback;
import java.time.format.DateTimeFormatter;

public record FeedbackDto(Long id, String username, String userName, String meterId, String area,
                          String type, String description, String status, String resolution,
                          String handledBy, String createdAt, String updatedAt) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static FeedbackDto from(UserFeedback feedback) {
        return new FeedbackDto(feedback.getId(), feedback.getUsername(), feedback.getDisplayName(),
                feedback.getMeterId(), feedback.getArea(), feedback.getType(), feedback.getDescription(),
                feedback.getStatus(), feedback.getResolution(), feedback.getHandledBy(),
                feedback.getCreatedAt().format(FMT), feedback.getUpdatedAt().format(FMT));
    }
}
