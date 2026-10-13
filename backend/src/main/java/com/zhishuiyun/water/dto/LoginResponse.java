package com.zhishuiyun.water.dto;

public record LoginResponse(
        String token,
        String username,
        String name,
        String role,
        Long id,
        String phone,
        String areaScope,
        String boundMeterId) {
}
