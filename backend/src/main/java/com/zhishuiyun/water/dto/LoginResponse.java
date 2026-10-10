package com.zhishuiyun.water.dto;

public record LoginResponse(
        String token,
        String username,
        String name,
        String role) {
}
