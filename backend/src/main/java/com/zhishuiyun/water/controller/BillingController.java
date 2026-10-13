package com.zhishuiyun.water.controller;

import com.zhishuiyun.water.common.ApiResponse;
import com.zhishuiyun.water.dto.BillBatchDto;
import com.zhishuiyun.water.security.CurrentUser;
import com.zhishuiyun.water.service.BillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;
import com.zhishuiyun.water.dto.BillItemDto;
import com.zhishuiyun.water.dto.PaymentDto;
import com.zhishuiyun.water.entity.PriceRule;

@RestController
@RequestMapping("/api/bills")
@RequiredArgsConstructor
public class BillingController {

    private final BillService billService;

    @GetMapping
    public ApiResponse<List<BillBatchDto>> bills() {
        return ApiResponse.ok(billService.listBills(CurrentUser.required()));
    }

    @PostMapping("/recalculate")
    public ApiResponse<List<BillBatchDto>> recalculate(@RequestBody(required = false) GenerateRequest request) {
        return ApiResponse.ok(billService.recalculate(CurrentUser.required(),
                request == null ? null : request.period(), request == null ? null : request.dueDate()));
    }

    @GetMapping("/items")
    public ApiResponse<List<BillItemDto>> items(@RequestParam(required = false) String batch,
                                               @RequestParam(required = false) String period) {
        return ApiResponse.ok(billService.listItems(CurrentUser.required(), batch, period));
    }

    @GetMapping("/payments")
    public ApiResponse<List<PaymentDto>> payments() {
        return ApiResponse.ok(billService.payments(CurrentUser.required()));
    }

    @PostMapping("/items/{id}/pay")
    public ApiResponse<PaymentDto> pay(@PathVariable Long id, @RequestBody PayRequest request) {
        return ApiResponse.ok(billService.pay(id, request.idempotencyKey(), CurrentUser.required()));
    }

    @GetMapping("/rules")
    public ApiResponse<List<PriceRule>> rules() {
        CurrentUser.required();
        return ApiResponse.ok(billService.rules());
    }

    public record GenerateRequest(String period, LocalDate dueDate) {}
    public record PayRequest(String idempotencyKey) {}
}
