package com.zhishuiyun.water.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(@NotBlank @Size(max = 60) String type,
                              @NotBlank @Size(min = 5, max = 2000) String description) {
}
