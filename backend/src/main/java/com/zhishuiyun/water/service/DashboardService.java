package com.zhishuiyun.water.service;

import com.zhishuiyun.water.dto.DashboardData;
import com.zhishuiyun.water.entity.*;
import com.zhishuiyun.water.repository.AnomalyRepository;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final ReadingRepository readingRepository;
    private final AnomalyRepository anomalyRepository;
    private final BillItemRepository billItemRepository;
    private final AccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public DashboardData getDashboard(AuthUser user) {
        List<Meter> meters = accessPolicy.visibleMeters(user);
        Set<String> meterIds = meters.stream().map(Meter::getMeterId).collect(Collectors.toSet());
        List<Reading> readings = readingRepository.findAll().stream()
                .filter(reading -> meterIds.contains(reading.getMeterId())).toList();
        LocalDate today = LocalDate.now();
        long reportedToday = readings.stream().filter(reading -> reading.getTs() != null
                        && reading.getTs().toLocalDate().equals(today) && !reading.getTs().isAfter(java.time.LocalDateTime.now()))
                .map(Reading::getMeterId).distinct().count();
        int coverage = meters.isEmpty() ? 0 : (int) (reportedToday * 100 / meters.size());
        String period = YearMonth.now().toString();
        List<BillItem> bills = billItemRepository.findAll().stream()
                .filter(item -> meterIds.contains(item.getMeterId()) && period.equals(item.getPeriod())).toList();
        BigDecimal receivable = bills.stream().map(BillService::currentTotalDue).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Anomaly> anomalies = anomalyRepository.findAll().stream()
                .filter(anomaly -> meterIds.contains(anomaly.getMeterId())).toList();
        long pending = anomalies.stream().filter(anomaly -> anomaly.getStatus() == DomainEnums.AnomalyStatus.待处理).count();
        long highRisk = anomalies.stream().filter(anomaly -> anomaly.getLevel() == DomainEnums.RiskLevel.高
                && anomaly.getStatus() != DomainEnums.AnomalyStatus.已完成).count();
        List<DashboardData.StatCard> stats = List.of(
                new DashboardData.StatCard("水表总数", String.valueOf(meters.size()), "当前账号可访问范围", "cyan"),
                new DashboardData.StatCard("今日抄表率", coverage + "%", "今日已上报水表去重占比", "green"),
                new DashboardData.StatCard("本月应收", "¥" + receivable.setScale(2, RoundingMode.HALF_UP).toPlainString(), "范围内账单明细汇总", "blue"),
                new DashboardData.StatCard("待处理异常", String.valueOf(pending), "未完成高风险 " + highRisk + " 条", "orange"));
        Map<LocalDate, BigDecimal> daily = UsageAnalytics.dailyUsage(readings, meterIds, today.minusDays(6), today);
        List<BigDecimal> trend = new ArrayList<>();
        for (int days = 6; days >= 0; days--) {
            trend.add(daily.getOrDefault(today.minusDays(days), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        }
        List<DashboardData.Category> categories = new ArrayList<>();
        for (DomainEnums.MeterType type : DomainEnums.MeterType.values()) {
            BigDecimal usage = meters.stream().filter(meter -> meter.getType() == type)
                    .map(meter -> currentUsage(meter, bills)).reduce(BigDecimal.ZERO, BigDecimal::add);
            categories.add(new DashboardData.Category(type.name(), usage.setScale(2, RoundingMode.HALF_UP)));
        }
        return new DashboardData(stats, trend, categories);
    }

    private BigDecimal currentUsage(Meter meter, List<BillItem> bills) {
        return bills.stream().filter(item -> meter.getMeterId().equals(item.getMeterId())).map(BillItem::getUsage)
                .filter(Objects::nonNull).findFirst().orElseGet(() -> meter.getReading() == null || meter.getLastReading() == null
                        ? BigDecimal.ZERO : meter.getReading().subtract(meter.getLastReading()).max(BigDecimal.ZERO));
    }
}
