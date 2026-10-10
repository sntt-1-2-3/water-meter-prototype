package com.zhishuiyun.water.service;

import com.zhishuiyun.water.dto.FeeResult;
import com.zhishuiyun.water.entity.DomainEnums.MeterType;
import com.zhishuiyun.water.entity.PriceRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 计费规则参数化测试：覆盖测试用例文档的 60 条 BILL-001 ~ BILL-060。
 */
class BillingServiceTest {

    private final BillingService billingService = new BillingService();

    @ParameterizedTest(name = "[{index}] {0} usage={1} days={2} -> {3}/{4}/{5}")
    @CsvSource(textBlock = """
            居民,22.53,10,75.56,0.38,75.93
            居民,24.22,0,84.34,0,84.34
            居民,21.8,0,71.76,0,71.76
            居民,19.11,0,59.2,0,59.2
            居民,25.85,0,92.82,0,92.82
            居民,14.71,0,43.36,0,43.36
            居民,22.67,0,76.28,0,76.28
            居民,20.21,0,63.49,0,63.49
            居民,19.31,0,59.92,0,59.92
            居民,25.62,15,91.62,0.69,92.31
            居民,20.67,0,65.88,0,65.88
            居民,14.08,0,41.09,0,41.09
            居民,19.61,0,61,0,61
            居民,23.12,0,78.62,0,78.62
            居民,15.28,0,45.41,0,45.41
            居民,23.71,0,81.69,0,81.69
            居民,25.9,0,93.08,0,93.08
            居民,22.38,0,74.78,0,74.78
            居民,25.13,20,89.08,0.89,89.97
            居民,25.45,0,90.74,0,90.74
            居民,19.88,0,61.97,0,61.97
            居民,24,0,83.2,0,83.2
            居民,20.33,0,64.12,0,64.12
            居民,15.7,0,46.92,0,46.92
            居民,24.77,0,87.2,0,87.2
            居民,21.64,0,70.93,0,70.93
            居民,17.86,0,54.7,0,54.7
            居民,25.31,25,90.01,1.13,91.14
            居民,15.51,0,46.24,0,46.24
            居民,25.49,0,90.95,0,90.95
            商业,101.94,0,428.15,0,428.15
            商业,80.93,0,339.91,0,339.91
            商业,114.85,0,482.37,0,482.37
            商业,88.28,0,370.78,0,370.78
            商业,147.3,0,618.66,0,618.66
            商业,137.29,0,576.62,0,576.62
            商业,130.47,10,547.97,2.74,550.71
            商业,122.38,0,514,0,514
            商业,135.22,0,567.92,0,567.92
            商业,103.85,0,436.17,0,436.17
            商业,128.36,0,539.11,0,539.11
            商业,80.84,0,339.53,0,339.53
            商业,165.46,0,694.93,0,694.93
            商业,140.59,0,590.48,0,590.48
            商业,137.1,0,575.82,0,575.82
            商业,94.09,15,395.18,2.96,398.14
            商业,112.91,0,474.22,0,474.22
            商业,110.63,0,464.65,0,464.65
            工业,948.45,0,4837.1,0,4837.1
            工业,904.7,0,4613.97,0,4613.97
            工业,1087,0,5543.7,0,5543.7
            工业,958.16,0,4886.62,0,4886.62
            工业,777.53,0,3965.4,0,3965.4
            工业,640.12,0,3264.61,0,3264.61
            工业,1054.77,20,5379.33,53.79,5433.12
            工业,632.12,0,3223.81,0,3223.81
            工业,555.14,0,2831.21,0,2831.21
            工业,882.8,0,4502.28,0,4502.28
            工业,692.99,0,3534.25,0,3534.25
            工业,582,0,2968.2,0,2968.2
            """)
    void testBilling(String type, String usage, int days, String waterFee, String penalty, String totalDue) {
        FeeResult result = billingService.calc(rulesFor(MeterType.valueOf(type)),
                new BigDecimal(usage), days);
        assertThat(result.waterFee()).isEqualByComparingTo(waterFee);
        assertThat(result.penalty()).isEqualByComparingTo(penalty);
        assertThat(result.totalDue()).isEqualByComparingTo(totalDue);
    }

    @Test
    void testKnownDiscrepancySample_18_6() {
        // 测试文档点名的 57.36 元样本（原型曾错误显示 56.80）
        FeeResult result = billingService.calc(residentRules(), new BigDecimal("18.6"), 0);
        assertThat(result.waterFee()).isEqualByComparingTo("57.36");
    }

    @Test
    void testTierBoundaries() {
        assertThat(billingService.calc(residentRules(), new BigDecimal("12"), 0).waterFee())
                .isEqualByComparingTo("33.60");
        assertThat(billingService.calc(residentRules(), new BigDecimal("20"), 0).waterFee())
                .isEqualByComparingTo("62.40");
    }

    private List<PriceRule> rulesFor(MeterType type) {
        return switch (type) {
            case 居民 -> residentRules();
            case 商业 -> flat("4.20");
            case 工业 -> flat("5.10");
        };
    }

    private List<PriceRule> residentRules() {
        return List.of(
                rule(1, "0", "12", "2.80"),
                rule(2, "12", "20", "3.60"),
                rule(3, "20", null, "5.20"));
    }

    private List<PriceRule> flat(String price) {
        return List.of(rule(1, "0", null, price));
    }

    private PriceRule rule(int tier, String from, String to, String price) {
        return PriceRule.builder()
                .tier(tier)
                .fromUsage(new BigDecimal(from))
                .toUsage(to == null ? null : new BigDecimal(to))
                .unitPrice(new BigDecimal(price))
                .penaltyRate(new BigDecimal("0.0005"))
                .build();
    }
}
