package com.zhishuiyun.water.controller;

import com.zhishuiyun.water.common.ApiResponse;
import com.zhishuiyun.water.dto.MeterDto;
import com.zhishuiyun.water.dto.MeterRequest;
import com.zhishuiyun.water.dto.PageResult;
import com.zhishuiyun.water.security.CurrentUser;
import com.zhishuiyun.water.service.MeterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meters")
@RequiredArgsConstructor
public class MeterController {

    private final MeterService meterService;

    @GetMapping
    public ApiResponse<PageResult<MeterDto>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type) {
        return ApiResponse.ok(meterService.page(page, pageSize, keyword, type, CurrentUser.required()));
    }

    @PostMapping
    public ApiResponse<MeterDto> create(@RequestBody MeterRequest request) {
        return ApiResponse.ok(meterService.create(request, CurrentUser.required()));
    }

    @PutMapping("/{id}")
    public ApiResponse<MeterDto> update(@PathVariable String id, @RequestBody MeterRequest request) {
        return ApiResponse.ok(meterService.update(id, request, CurrentUser.required()));
    }
}
