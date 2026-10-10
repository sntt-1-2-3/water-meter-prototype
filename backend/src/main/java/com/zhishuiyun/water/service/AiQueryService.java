package com.zhishuiyun.water.service;

import com.zhishuiyun.water.common.BusinessException;
import com.zhishuiyun.water.common.ErrorCode;
import com.zhishuiyun.water.entity.Anomaly;
import com.zhishuiyun.water.entity.Bill;
import com.zhishuiyun.water.entity.Meter;
import com.zhishuiyun.water.repository.AnomalyRepository;
import com.zhishuiyun.water.repository.BillRepository;
import com.zhishuiyun.water.repository.MeterRepository;
import com.zhishuiyun.water.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 问数。优先调用 DeepSeek 大模型（需配置 DEEPSEEK_API_KEY），未配置时回退到本地规则引擎。
 * 基于真实数据库回答，回答带数据范围与来源；越权 / 提示注入直接拒绝；无数据明确说明不编造。
 */
@Service
@RequiredArgsConstructor
public class AiQueryService {

    private final MeterRepository meterRepository;
    private final AnomalyRepository anomalyRepository;
    private final BillRepository billRepository;
    private final DeepSeekClient deepSeekClient;

    private static final String SYSTEM_PROMPT =
            "你是「智水云」AI 水表抄表收费管理系统的数据分析助手。"
            + "请仅基于下方提供的系统实时数据回答用户问题，不得编造或推测未提供的数据。"
            + "若所需信息不在数据范围内，请明确说明“当前数据范围内未查询到相关信息”。"
            + "回答使用简体中文，简洁、结构清晰；金额保留两位小数，水量保留一位小数。";

    public String query(String question, AuthUser user) {
        String q = question == null ? "" : question.trim();
        String lower = q.toLowerCase();

        // 越权 / 注入拦截
        if (containsAny(lower, "忽略权限", "导出全部", "全部用户", "修改账单", "删除", "密钥", "密码", "sql", "drop ", "绕过")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该请求涉及越权或敏感操作，已拒绝执行");
        }

        // 已配置 DeepSeek 时用大模型处理数据，否则回退到本地规则引擎
        if (deepSeekClient.isAvailable()) {
            return queryWithDeepSeek(q, user);
        }
        return queryByRules(q, user);
    }

    private String queryByRules(String q, AuthUser user) {
        if (q.contains("异常")) {
            return answerAnomalies(user);
        }
        if (q.contains("小区") || q.contains("区域") || q.contains("最高") || q.contains("用水量")) {
            return answerTopArea(user);
        }
        if (q.contains("账单") || q.contains("收费") || q.contains("应收") || q.contains("缴费")) {
            return answerBilling(user);
        }
        return answerSummary(user);
    }

    private String queryWithDeepSeek(String question, AuthUser user) {
        String context = buildDataContext(user);
        return deepSeekClient.chat(SYSTEM_PROMPT + "\n\n系统实时数据：\n" + context, question);
    }

    /** 汇总当前用户数据范围内的水表、异常、账单，作为 DeepSeek 的数据上下文。 */
    private String buildDataContext(AuthUser user) {
        StringBuilder sb = new StringBuilder();
        sb.append("数据范围：").append(scopeText(user)).append("；统计时间 ").append(LocalDate.now()).append("。\n");

        List<Meter> meters = metersInScope(user);
        Map<String, BigDecimal> areaUsage = new HashMap<>();
        for (Meter m : meters) {
            if (m.getReading() != null && m.getLastReading() != null) {
                BigDecimal usage = m.getReading().subtract(m.getLastReading());
                if (usage.signum() >= 0) {
                    areaUsage.merge(m.getArea(), usage, BigDecimal::add);
                }
            }
        }
        sb.append("水表：范围内共 ").append(meters.size()).append(" 台。");
        if (!areaUsage.isEmpty()) {
            sb.append("各区域本月用水量(m³)：");
            areaUsage.entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .forEach(e -> sb.append(e.getKey()).append("≈")
                            .append(e.getValue().setScale(1, RoundingMode.HALF_UP).toPlainString()).append("；"));
        }
        sb.append("\n");

        List<Anomaly> anomalies = anomalyRepository.findAllByOrderByIdDesc().stream()
                .filter(a -> user.canAccessArea(a.getArea()))
                .toList();
        sb.append("异常：").append(anomalies.isEmpty() ? "无" : "共 " + anomalies.size() + " 条");
        anomalies.stream().limit(5).forEach(a -> sb.append("\n· ").append(a.getAnomalyId())
                .append(" ").append(a.getMeterId()).append("（").append(a.getArea()).append("）")
                .append(a.getType()).append("，").append(a.getLevel().name())
                .append("风险，状态：").append(a.getStatus().name()));
        sb.append("\n");

        String period = String.format("%04d-%02d", LocalDate.now().getYear(), LocalDate.now().getMonthValue());
        List<Bill> bills = billRepository.findAllByOrderByBatchNoDesc().stream()
                .filter(b -> period.equals(b.getPeriod()))
                .toList();
        if (bills.isEmpty()) {
            sb.append("账单：本月尚未生成。\n");
        } else {
            BigDecimal total = bills.stream().map(Bill::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paid = bills.stream().map(b -> b.getTotalPaid() != null ? b.getTotalPaid() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            sb.append("账单：本月 ").append(bills.size()).append(" 个批次，应收合计 ¥")
                    .append(total.setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .append("，已收 ¥").append(paid.setScale(2, RoundingMode.HALF_UP).toPlainString()).append("。\n");
        }
        return sb.toString();
    }

    private String answerAnomalies(AuthUser user) {
        List<Anomaly> anomalies = anomalyRepository.findAllByOrderByIdDesc().stream()
                .filter(a -> user.canAccessArea(a.getArea()))
                .toList();
        if (anomalies.isEmpty()) {
            return noData("异常记录");
        }
        StringBuilder sb = new StringBuilder("当前范围内待关注异常 ").append(anomalies.size()).append(" 条：");
        anomalies.stream().limit(5).forEach(a -> sb.append("\n· ")
                .append(a.getAnomalyId()).append(" ").append(a.getMeterId())
                .append("（").append(a.getArea()).append("）").append(a.getType())
                .append("，").append(a.getLevel().name()).append("风险，状态：").append(a.getStatus().name()));
        sb.append("\n数据范围：").append(scopeText(user)).append("；来源：异常中心，统计时间 ")
                .append(LocalDate.now());
        return sb.toString();
    }

    private String answerTopArea(AuthUser user) {
        Map<String, BigDecimal> areaUsage = new HashMap<>();
        for (Meter m : metersInScope(user)) {
            if (m.getReading() != null && m.getLastReading() != null) {
                BigDecimal usage = m.getReading().subtract(m.getLastReading());
                if (usage.signum() >= 0) {
                    areaUsage.merge(m.getArea(), usage, BigDecimal::add);
                }
            }
        }
        if (areaUsage.isEmpty()) {
            return noData("区域用水量");
        }
        Map.Entry<String, BigDecimal> top = areaUsage.entrySet().stream()
                .max(Comparator.comparing(Map.Entry::getValue)).orElseThrow();
        long total = areaUsage.values().stream().mapToLong(v -> v.setScale(0, RoundingMode.HALF_UP).longValue()).sum();
        return "本月用水量最高的是「" + top.getKey() + "」，累计约 "
                + top.getValue().setScale(1, RoundingMode.HALF_UP).toPlainString()
                + " m³，占全部区域约 " + (total == 0 ? 0 : top.getValue().multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP)) + "%。"
                + "\n数据范围：" + scopeText(user) + "；来源：水表档案与上报数据，统计时间 " + LocalDate.now();
    }

    private String answerBilling(AuthUser user) {
        String period = String.format("%04d-%02d", LocalDate.now().getYear(), LocalDate.now().getMonthValue());
        List<Bill> bills = billRepository.findAllByOrderByBatchNoDesc().stream()
                .filter(b -> period.equals(b.getPeriod()))
                .toList();
        if (bills.isEmpty()) {
            return "本月账单尚未生成。数据范围：" + scopeText(user) + "；来源：账单库。";
        }
        BigDecimal total = bills.stream().map(Bill::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = bills.stream().map(b -> b.getTotalPaid() != null ? b.getTotalPaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        StringBuilder sb = new StringBuilder("本月已生成 ").append(bills.size()).append(" 个账单批次，应收合计 ¥")
                .append(total.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .append("，已收 ¥").append(paid.setScale(2, RoundingMode.HALF_UP).toPlainString()).append("：");
        bills.forEach(b -> sb.append("\n· ").append(b.getBatchNo())
                .append("：").append(b.getUserCount()).append(" 户，应收 ¥")
                .append(b.getTotalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString())
                .append("，").append(b.getStatus()));
        sb.append("\n数据范围：").append(scopeText(user)).append("；来源：账单库，统计时间 ").append(LocalDate.now());
        return sb.toString();
    }

    private String answerSummary(AuthUser user) {
        long meters = metersInScope(user).size();
        long anomalies = anomalyRepository.countByStatus(com.zhishuiyun.water.entity.DomainEnums.AnomalyStatus.待处理);
        return "运营摘要：范围内水表 " + meters + " 台，待处理异常 " + anomalies + " 条。"
                + "\n数据范围：" + scopeText(user) + "；来源：水表档案与异常中心，统计时间 " + LocalDate.now();
    }

    private List<Meter> metersInScope(AuthUser user) {
        return meterRepository.findAll().stream()
                .filter(m -> user.canAccessArea(m.getArea()))
                .filter(m -> user.getBoundMeterId() == null || user.getBoundMeterId().equals(m.getMeterId()))
                .toList();
    }

    private String scopeText(AuthUser user) {
        if (user.getAreaScope() != null) {
            return user.getAreaScope();
        }
        if (user.getBoundMeterId() != null) {
            return "本人水表 " + user.getBoundMeterId();
        }
        return "全部区域";
    }

    private String noData(String subject) {
        return "未查询到" + subject + "相关数据，无法给出结论。请确认查询范围或待数据上报后再试。";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }
}
