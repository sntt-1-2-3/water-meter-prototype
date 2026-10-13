package com.zhishuiyun.water.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,40}", message = "用户名须为3至40位字母、数字或下划线") String username,
        @NotBlank @Size(min = 8, max = 72, message = "密码须为8至72个字符") String password,
        @NotBlank @Size(max = 60) String name,
        @Size(max = 30) String phone) {
}
