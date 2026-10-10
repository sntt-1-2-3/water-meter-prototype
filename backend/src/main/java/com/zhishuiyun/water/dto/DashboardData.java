package com.zhishuiyun.water.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 运营总览数据，对齐前端 DashboardData。
 */
@Getter
@AllArgsConstructor
public class DashboardData {

    private final List<StatCard> stats;
    private final List<java.math.BigDecimal> trend;
    private final List<Category> categories;

    @Getter
    @AllArgsConstructor
    public static class StatCard {
        private final String label;
        private final String value;
        private final String note;
        private final String tone;
    }

    @Getter
    @AllArgsConstructor
    public static class Category {
        private final String name;
        private final java.math.BigDecimal value;
    }
}
