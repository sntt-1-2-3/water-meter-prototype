package com.zhishuiyun.water.service;

import com.zhishuiyun.water.dto.DashboardData;
import com.zhishuiyun.water.entity.Bill;
import com.zhishuiyun.water.entity.DomainEnums;
import com.zhishuiyun.water.entity.Reading;
import com.zhishuiyun.water.repository.AnomalyRepository;
import com.zhishuiyun.water.repository.BillRepository;
import com.zhishuiyun.water.repository.MeterRepository;
import com.zhishuiyun.water.repository.ReadingRepository;
import com.zhishuiyun.water.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final MeterRepository meterRepository;
    private final ReadingRepository readingRepository;
    private final AnomalyRepository anomalyRepository;
    private final BillRepository billRepository;

    public DashboardData getDashboard(AuthUser user) {
        long totalMeters = meterRepository.count();

        // 今日抄表率：近 24 小时有上报的表占比
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        long reportedToday = readingRepository.countByTsBetween(dayStart, LocalDateTime.now());
        int coverage = totalMeters == 0 ? 0
                : (int) Math.min(100, reportedToday * 100 / totalMeters);

        // 本月应收：本月账单批次合计
        String period = String.format("%04d-%02d", LocalDate.now().getYear(), LocalDate.now().getMonthValue());
        BigDecimal receivable = billRepository.findAllByOrderByBatchNoDesc().stream()
                .filter(b -> period.equals(b.getPeriod()))
                .map(Bill::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pending = anomalyRepository.countByStatus(DomainEnums.AnomalyStatus.待处理);
        long highRisk = anomalyRepository.countByLevel(DomainEnums.RiskLevel.高);

        List<DashboardData.StatCard> stats = List.of(
                new DashboardData.StatCard("水表总数", String.valueOf(totalMeters),
                        "已纳入台账", "cyan"),
                new DashboardData.StatCard("今日抄表率", coverage + "%",
                        "近 24 小时上报占比", "green"),
                new DashboardData.StatCard("本月应收", "¥" + receivable.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        "按已生成账单统计", "blue"),
                new DashboardData.StatCard("待处理异常", String.valueOf(pending),
                        "高风险 " + highRisk + " 条", "orange"));

        // 近 7 日用水量（按上报的瞬时流量按天聚合，单位为 m³）
        LocalDateTime from = LocalDate.now().minusDays(6).atStartOfDay();
        List<Reading> readings = readingRepository.findByTsBetween(from, LocalDateTime.now());
        Map<LocalDate, BigDecimal> daily = new TreeMap<>();
        for (Reading r : readings) {
            if (r.getInstantFlow() != null) {
                daily.merge(r.getTs().toLocalDate(), r.getInstantFlow(), BigDecimal::add);
            }
        }
        List<BigDecimal> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            trend.add(daily.getOrDefault(LocalDate.now().minusDays(i), BigDecimal.ZERO)
                    .setScale(0, RoundingMode.HALF_UP));
        }

        // 本月用水结构（按用户类型台数）
        List<DashboardData.Category> categories = List.of(
                new DashboardData.Category("居民", BigDecimal.valueOf(meterRepository.countByType(DomainEnums.MeterType.居民))),
                new DashboardData.Category("商业", BigDecimal.valueOf(meterRepository.countByType(DomainEnums.MeterType.商业))),
                new DashboardData.Category("工业", BigDecimal.valueOf(meterRepository.countByType(DomainEnums.MeterType.工业))));

        return new DashboardData(stats, trend, categories);
    }
}
