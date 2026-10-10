package com.zhishuiyun.water.service;

import com.zhishuiyun.water.dto.FeeResult;
import com.zhishuiyun.water.entity.PriceRule;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 计费核心。金额全程 BigDecimal + RoundingMode.HALF_UP，统一到「分」。
 *
 * <p>规则（与测试数据一致）：
 * <ul>
 *   <li>居民阶梯：0-12 @2.80、12-20 @3.60、&gt;20 @5.20</li>
 *   <li>商业统一价：4.20；工业合同价：5.10</li>
 *   <li>违约金 = 水费 × 日费率(0.0005) × 逾期天数</li>
 *   <li>应缴 = round(未舍入水费 + 未舍入违约金) —— 逐项舍入会导致 1 分差异</li>
 * </ul>
 */
@Service
public class BillingService {

    private static final BigDecimal DEFAULT_PENALTY_RATE = new BigDecimal("0.0005");

    /**
     * 纯计算入口，便于单元测试（rules 按 tier 升序）。
     */
    public FeeResult calc(List<PriceRule> rules, BigDecimal usage, int overdueDays) {
        BigDecimal rawWaterFee = waterFeeRaw(usage, rules);
        BigDecimal penaltyRate = rules.isEmpty() ? BigDecimal.ZERO
                : (rules.get(0).getPenaltyRate() != null ? rules.get(0).getPenaltyRate() : DEFAULT_PENALTY_RATE);
        int days = Math.max(0, overdueDays);

        BigDecimal waterFee = rawWaterFee.setScale(2, RoundingMode.HALF_UP);
        BigDecimal penaltyRaw = rawWaterFee.multiply(penaltyRate).multiply(BigDecimal.valueOf(days));
        BigDecimal penalty = penaltyRaw.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDue = rawWaterFee.add(penaltyRaw).setScale(2, RoundingMode.HALF_UP);

        return new FeeResult(waterFee, penalty, totalDue);
    }

    /**
     * 阶梯累加，返回未舍入的精确水费。
     */
    private BigDecimal waterFeeRaw(BigDecimal usage, List<PriceRule> rules) {
        BigDecimal remaining = usage;
        BigDecimal fee = BigDecimal.ZERO;
        for (PriceRule rule : rules) {
            if (rule.getFromUsage() == null || rule.getUnitPrice() == null) {
                continue;
            }
            BigDecimal band;
            if (rule.getToUsage() == null) {
                band = remaining;
            } else {
                band = remaining.min(rule.getToUsage().subtract(rule.getFromUsage()));
            }
            if (band.signum() > 0) {
                fee = fee.add(band.multiply(rule.getUnitPrice()));
                remaining = remaining.subtract(band);
            }
            if (remaining.signum() <= 0) {
                break;
            }
        }
        return fee;
    }
}
