package com.zhishuiyun.water.service;

import com.zhishuiyun.water.common.BusinessException;
import com.zhishuiyun.water.common.ErrorCode;
import com.zhishuiyun.water.dto.UserSummaryDto;
import com.zhishuiyun.water.entity.BillItem;
import com.zhishuiyun.water.entity.Meter;
import com.zhishuiyun.water.repository.BillItemRepository;
import com.zhishuiyun.water.repository.ReadingRepository;
import com.zhishuiyun.water.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserSummaryService {
    private final AccessPolicy accessPolicy;
    private final BillItemRepository billItemRepository;
    private final ReadingRepository readingRepository;

    @Transactional(readOnly = true)
    public UserSummaryDto summary(AuthUser user) {
        if (!user.isUser()) throw new BusinessException(ErrorCode.FORBIDDEN);
        List<Meter> visible = accessPolicy.visibleMeters(user);
        if (visible.isEmpty()) return new UserSummaryDto(user.getName(), null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, "未绑定水表", BigDecimal.ZERO, List.of());
        Meter meter = visible.get(0);
        BillItem bill = billItemRepository.findFirstByMeterIdAndPeriod(meter.getMeterId(), YearMonth.now().toString()).orElse(null);
        BigDecimal usage = bill != null && bill.getUsage() != null ? bill.getUsage()
                : meter.getReading() == null || meter.getLastReading() == null ? BigDecimal.ZERO
                : meter.getReading().subtract(meter.getLastReading()).max(BigDecimal.ZERO);
        BigDecimal amount = bill == null ? BigDecimal.ZERO : BillService.currentTotalDue(bill);
        BigDecimal paid = bill == null || bill.getPaidAmount() == null ? BigDecimal.ZERO : bill.getPaidAmount();
        LocalDate today = LocalDate.now();
        Map<LocalDate, BigDecimal> daily = UsageAnalytics.dailyUsage(readingRepository.findAll(), Set.of(meter.getMeterId()), today.minusDays(29), today);
        List<UserSummaryDto.TrendPoint> trend = new ArrayList<>();
        for (int days = 29; days >= 0; days--) {
            LocalDate date = today.minusDays(days);
            trend.add(new UserSummaryDto.TrendPoint(date.toString(), daily.getOrDefault(date, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)));
        }
        return new UserSummaryDto(user.getName(), meter.getMeterId(), meter.getArea(), usage.setScale(2, RoundingMode.HALF_UP),
                amount, paid, amount.subtract(paid).max(BigDecimal.ZERO), bill == null ? "未出账" : bill.getStatus().name(),
                meter.getReading(), trend);
    }
}
