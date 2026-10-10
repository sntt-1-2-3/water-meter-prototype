package com.zhishuiyun.water.controller;

import com.zhishuiyun.water.common.ApiResponse;
import com.zhishuiyun.water.dto.BillBatchDto;
import com.zhishuiyun.water.security.CurrentUser;
import com.zhishuiyun.water.service.BillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bills")
@RequiredArgsConstructor
public class BillingController {

    private final BillService billService;

    @GetMapping
    public ApiResponse<List<BillBatchDto>> bills() {
        return ApiResponse.ok(billService.listBills());
    }

    @PostMapping("/recalculate")
    public ApiResponse<List<BillBatchDto>> recalculate() {
        return ApiResponse.ok(billService.recalculate(CurrentUser.required()));
    }
}
