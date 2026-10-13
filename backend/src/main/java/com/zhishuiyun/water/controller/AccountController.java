package com.zhishuiyun.water.controller;

import com.zhishuiyun.water.common.ApiResponse;
import com.zhishuiyun.water.dto.AccountProfile;
import com.zhishuiyun.water.dto.BindingRequest;
import com.zhishuiyun.water.security.CurrentUser;
import com.zhishuiyun.water.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;

    @GetMapping
    public ApiResponse<List<AccountProfile>> list() {
        return ApiResponse.ok(accountService.list(CurrentUser.required()));
    }

    @PatchMapping("/{id}/binding")
    public ApiResponse<AccountProfile> bind(@PathVariable Long id, @RequestBody BindingRequest request) {
        return ApiResponse.ok(accountService.bind(id, request.boundMeterId(), CurrentUser.required()));
    }
}
