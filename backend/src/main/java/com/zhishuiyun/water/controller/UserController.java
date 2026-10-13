package com.zhishuiyun.water.controller;

import com.zhishuiyun.water.common.ApiResponse;
import com.zhishuiyun.water.dto.UserSummaryDto;
import com.zhishuiyun.water.security.CurrentUser;
import com.zhishuiyun.water.service.UserSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserSummaryService userSummaryService;

    @GetMapping("/summary")
    public ApiResponse<UserSummaryDto> summary() {
        return ApiResponse.ok(userSummaryService.summary(CurrentUser.required()));
    }
}
