package com.zhishuiyun.water.controller;

import com.zhishuiyun.water.common.ApiResponse;
import com.zhishuiyun.water.dto.ReadingTaskDto;
import com.zhishuiyun.water.dto.ReportRequest;
import com.zhishuiyun.water.security.CurrentUser;
import com.zhishuiyun.water.service.ReadingService;
import com.zhishuiyun.water.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/readings")
@RequiredArgsConstructor
public class ReadingController {

    private final ReadingService readingService;
    private final ReportService reportService;

    @PostMapping("/tasks")
    public ApiResponse<ReadingTaskDto> start() {
        return ApiResponse.ok(readingService.startReading(CurrentUser.required()));
    }

    @GetMapping("/tasks")
    public ApiResponse<List<ReadingTaskDto>> tasks() {
        return ApiResponse.ok(readingService.listTasks());
    }

    /** 原始上报数据入库（DQ 校验）。 */
    @PostMapping
    public ApiResponse<Void> report(@RequestBody ReportRequest request) {
        reportService.report(request, CurrentUser.required());
        return ApiResponse.ok();
    }
}
