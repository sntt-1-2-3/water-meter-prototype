package com.zhishuiyun.water.controller;

import com.zhishuiyun.water.common.ApiResponse;
import com.zhishuiyun.water.dto.FeedbackDto;
import com.zhishuiyun.water.dto.FeedbackRequest;
import com.zhishuiyun.water.dto.FeedbackUpdateRequest;
import com.zhishuiyun.water.security.CurrentUser;
import com.zhishuiyun.water.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping({"/api/feedback", "/api/feedbacks"})
@RequiredArgsConstructor
public class FeedbackController {
    private final FeedbackService feedbackService;

    @GetMapping
    public ApiResponse<List<FeedbackDto>> list() {
        return ApiResponse.ok(feedbackService.list(CurrentUser.required()));
    }

    @PostMapping
    public ApiResponse<FeedbackDto> submit(@Valid @RequestBody FeedbackRequest request) {
        return ApiResponse.ok(feedbackService.submit(request, CurrentUser.required()));
    }

    @PatchMapping("/{id}")
    public ApiResponse<FeedbackDto> update(@PathVariable Long id, @Valid @RequestBody FeedbackUpdateRequest request) {
        return ApiResponse.ok(feedbackService.update(id, request, CurrentUser.required()));
    }
}
