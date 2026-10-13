package com.zhishuiyun.water.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FeedbackUpdateRequest(@NotBlank @Pattern(regexp = "处理中|已完成") String status,
                                    @Size(max = 2000) String resolution) {
}
