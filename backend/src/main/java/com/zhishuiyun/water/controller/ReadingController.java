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
    public ApiResponse<ReadingTaskDto> start(@RequestBody(required = false) TaskRequest request) {
        return ApiResponse.ok(readingService.startReading(CurrentUser.required(), request != null && Boolean.TRUE.equals(request.simulateFailure())));
    }

    @GetMapping("/tasks")
    public ApiResponse<List<ReadingTaskDto>> tasks() {
        return ApiResponse.ok(readingService.listTasks(CurrentUser.required()));
    }

    @PostMapping("/tasks/{id}/retry")
    public ApiResponse<ReadingTaskDto> retry(@PathVariable Long id) {
        return ApiResponse.ok(readingService.retry(id, CurrentUser.required()));
    }

    @PostMapping("/simulate")
    public ApiResponse<Void> simulate(@RequestBody SimulationRequest request) {
        readingService.simulate(request.meterId(), request.scenario(), CurrentUser.required());
        return ApiResponse.ok();
    }

    @GetMapping("/scheduler")
    public ApiResponse<ReadingService.SchedulerState> scheduler() {
        return ApiResponse.ok(readingService.scheduler(CurrentUser.required()));
    }

    @PatchMapping("/scheduler")
    public ApiResponse<ReadingService.SchedulerState> scheduler(@RequestBody SchedulerRequest request) {
        return ApiResponse.ok(readingService.setScheduler(Boolean.TRUE.equals(request.enabled()), CurrentUser.required()));
    }

    public record TaskRequest(Boolean simulateFailure) {}
    public record SimulationRequest(String meterId, String scenario) {}
    public record SchedulerRequest(Boolean enabled) {}

    /** 原始上报数据入库（DQ 校验）。 */
    @PostMapping
    public ApiResponse<Void> report(@RequestBody ReportRequest request) {
        reportService.report(request, CurrentUser.required());
        return ApiResponse.ok();
    }
}
