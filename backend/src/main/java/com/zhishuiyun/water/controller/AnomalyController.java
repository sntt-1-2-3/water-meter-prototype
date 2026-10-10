package com.zhishuiyun.water.controller;

import com.zhishuiyun.water.common.ApiResponse;
import com.zhishuiyun.water.dto.AnomalyDto;
import com.zhishuiyun.water.security.CurrentUser;
import com.zhishuiyun.water.service.AnomalyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/anomalies")
@RequiredArgsConstructor
public class AnomalyController {

    private final AnomalyService anomalyService;

    @GetMapping
    public ApiResponse<List<AnomalyDto>> list(@RequestParam(required = false) String level) {
        return ApiResponse.ok(anomalyService.list(level, CurrentUser.required()));
    }

    @PatchMapping("/{id}")
    public ApiResponse<AnomalyDto> updateStatus(@PathVariable String id,
                                                @RequestBody Map<String, String> body) {
        return ApiResponse.ok(anomalyService.updateStatus(id, body.get("status"), CurrentUser.required()));
    }
}
