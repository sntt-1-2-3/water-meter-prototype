package com.zhishuiyun.water.service;

import com.zhishuiyun.water.entity.Reading;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** 用累计读数相邻增量统计水量；瞬时流量不能直接当作体积求和。 */
public final class UsageAnalytics {
    private UsageAnalytics() { }

    public static Map<LocalDate, BigDecimal> dailyUsage(List<Reading> readings, Set<String> meterIds,
                                                       LocalDate from, LocalDate to) {
        Map<String, BigDecimal> previous = new HashMap<>();
        Map<LocalDate, BigDecimal> result = new TreeMap<>();
        readings.stream().filter(reading -> meterIds.contains(reading.getMeterId())
                        && reading.getTs() != null && reading.getCumulativeFlow() != null
                        && !reading.getTs().isAfter(LocalDateTime.now()))
                .sorted(Comparator.comparing(Reading::getTs).thenComparing(reading -> reading.getId() == null ? 0L : reading.getId()))
                .forEach(reading -> {
                    BigDecimal last = previous.put(reading.getMeterId(), reading.getCumulativeFlow());
                    LocalDate day = reading.getTs().toLocalDate();
                    if (last != null && !day.isBefore(from) && !day.isAfter(to)) {
                        BigDecimal delta = reading.getCumulativeFlow().subtract(last);
                        if (delta.signum() >= 0) result.merge(day, delta, BigDecimal::add);
                    }
                });
        return result;
    }
}
