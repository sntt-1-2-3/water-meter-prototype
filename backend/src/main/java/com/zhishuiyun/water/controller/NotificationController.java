package com.zhishuiyun.water.controller;

import com.zhishuiyun.water.common.ApiResponse;
import com.zhishuiyun.water.dto.NotificationDto;
import com.zhishuiyun.water.security.CurrentUser;
import com.zhishuiyun.water.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<List<NotificationDto>> list() {
        return ApiResponse.ok(notificationService.list(CurrentUser.required()));
    }

    @GetMapping("/{id}")
    public ApiResponse<NotificationDto> get(@PathVariable Long id) {
        return ApiResponse.ok(notificationService.get(id, CurrentUser.required()));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationDto> read(@PathVariable Long id) {
        return ApiResponse.ok(notificationService.markRead(id, CurrentUser.required()));
    }

    @PostMapping("/read-all")
    public ApiResponse<Map<String, Integer>> readAll() {
        return ApiResponse.ok(Map.of("updated", notificationService.markAllRead(CurrentUser.required())));
    }
}
