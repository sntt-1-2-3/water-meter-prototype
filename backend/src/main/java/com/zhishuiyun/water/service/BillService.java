package com.zhishuiyun.water.service;

import com.zhishuiyun.water.common.BusinessException;
import com.zhishuiyun.water.common.ErrorCode;
import com.zhishuiyun.water.common.RequestContext;
import com.zhishuiyun.water.dto.BillBatchDto;
import com.zhishuiyun.water.dto.FeeResult;
import com.zhishuiyun.water.entity.*;
import com.zhishuiyun.water.repository.*;
import com.zhishuiyun.water.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 账单生成：按用户类型分批次，幂等，违约金由 BillingService 保证（单测覆盖）。
 */
@Service
@RequiredArgsConstructor
public class BillService {

    private final MeterRepository meterRepository;
    private final PriceRuleRepository priceRuleRepository;
    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final BillingService billingService;
    private final AuditService auditService;

    @Transactional
    public List<BillBatchDto> recalculate(AuthUser user) {
        String period = YearMonth.now().toString();
        if (billRepository.findByBatchNo(period + "-居民").isPresent()) {
            throw new BusinessException(ErrorCode.BILL_ALREADY_EXISTS);
        }

        Map<DomainEnums.MeterType, List<Meter>> grouped = new EnumMap<>(DomainEnums.MeterType.class);
        for (DomainEnums.MeterType type : DomainEnums.MeterType.values()) {
            grouped.put(type, new ArrayList<>());
        }
        for (Meter m : meterRepository.findAll()) {
            grouped.get(m.getType()).add(m);
        }

        List<BillBatchDto> result = new ArrayList<>();
        for (DomainEnums.MeterType type : DomainEnums.MeterType.values()) {
            List<PriceRule> rules = priceRuleRepository.findByMeterTypeOrderByTierAsc(type);
            List<Meter> meters = grouped.get(type);
            BigDecimal totalAmount = BigDecimal.ZERO;
            int count = 0;
            for (Meter m : meters) {
                BigDecimal usage = usageOf(m);
                if (usage == null) {
                    continue; // 累计倒退或缺读数，计费前置校验跳过
                }
                FeeResult fee = billingService.calc(rules, usage, 0);
                billItemRepository.save(BillItem.builder()
                        .batchNo(period + "-" + type.name())
                        .period(period)
                        .meterId(m.getMeterId())
                        .userName(m.getUserName())
                        .area(m.getArea())
                        .meterType(type)
                        .usage(usage)
                        .waterFee(fee.waterFee())
                        .penalty(fee.penalty())
                        .totalDue(fee.totalDue())
                        .paidAmount(BigDecimal.ZERO)
                        .overdueDays(0)
                        .status(DomainEnums.BillStatus.待缴)
                        .build());
                totalAmount = totalAmount.add(fee.totalDue());
                count++;
            }
            Bill batch = Bill.builder()
                    .batchNo(period + "-" + type.name())
                    .period(period)
                    .meterType(type)
                    .userCount(count)
                    .totalAmount(totalAmount)
                    .totalPaid(BigDecimal.ZERO)
                    .status("收费中")
                    .createdAt(LocalDateTime.now())
                    .build();
            billRepository.save(batch);
            result.add(BillBatchDto.from(batch));
        }
        auditService.log(user.getUsername(), user.getRole().name(), "RECALCULATE_BILL", period,
                RequestContext.getRequestId(), "SUCCESS", null, null);
        return result;
    }

    @Transactional(readOnly = true)
    public List<BillBatchDto> listBills() {
        return billRepository.findAllByOrderByBatchNoDesc().stream().map(BillBatchDto::from).toList();
    }

    /** 用量 = 本期 - 上期；倒退返回 null（计费前置校验跳过）。 */
    private BigDecimal usageOf(Meter m) {
        if (m.getReading() == null || m.getLastReading() == null) {
            return null;
        }
        BigDecimal usage = m.getReading().subtract(m.getLastReading());
        return usage.signum() >= 0 ? usage : null;
    }
}
