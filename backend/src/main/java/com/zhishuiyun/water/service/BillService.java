package com.zhishuiyun.water.service;

import com.zhishuiyun.water.common.*;
import com.zhishuiyun.water.dto.*;
import com.zhishuiyun.water.entity.*;
import com.zhishuiyun.water.repository.*;
import com.zhishuiyun.water.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class BillService {
    private final MeterRepository meterRepository;
    private final ReadingRepository readingRepository;
    private final PriceRuleRepository priceRuleRepository;
    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final PaymentRepository paymentRepository;
    private final BillingService billingService;
    private final AuditService auditService;
    private final AccessPolicy accessPolicy;
    private final NotificationService notifications;

    public List<BillBatchDto> recalculate(AuthUser user) {
        return recalculate(user, null, null);
    }

    /** Each meter/period is generated once. Existing snapshots (including paid items) remain immutable. */
    public List<BillBatchDto> recalculate(AuthUser user, String requestedPeriod, LocalDate dueDate) {
        accessPolicy.requireBilling(user);
        YearMonth month;
        try { month = requestedPeriod == null || requestedPeriod.isBlank()
                ? YearMonth.now() : YearMonth.parse(requestedPeriod); }
        catch (Exception e) { throw new BusinessException(ErrorCode.BAD_REQUEST, "账期格式应为 yyyy-MM"); }
        if (month.isAfter(YearMonth.now())) throw new BusinessException(ErrorCode.BAD_REQUEST, "不能生成未来账期");
        String period = month.toString();
        LocalDate deadline = dueDate == null ? month.atEndOfMonth().plusDays(15) : dueDate;
        if (deadline.isBefore(month.atDay(1))) throw new BusinessException(ErrorCode.BAD_REQUEST, "缴费截止日期不能早于账期");
        int created = 0;
        for (Meter meter : meterRepository.findAll()) {
            if (billItemRepository.findFirstByMeterIdAndPeriod(meter.getMeterId(), period).isPresent()) continue;
            // Inserting an older period after a later snapshot would double-count usage.
            List<BillItem> previous = billItemRepository.findByMeterIdOrderByIdDesc(meter.getMeterId());
            if (previous.stream().anyMatch(item -> item.getPeriod().compareTo(period) > 0))
                throw new BusinessException(ErrorCode.BAD_REQUEST, "已有后续账期，不能补建更早的账单");
            BillItem preceding = previous.stream().filter(item -> item.getPeriod().compareTo(period) < 0)
                    .max(Comparator.comparing(BillItem::getPeriod)).orElse(null);
            BigDecimal opening = preceding != null && preceding.getClosingReading() != null
                    ? preceding.getClosingReading() : openingAt(meter, month);
            BigDecimal closing = closingAt(meter, month);
            if (opening == null || closing == null || closing.compareTo(opening) < 0)
                throw new BusinessException(ErrorCode.BILL_NOT_READY, "水表 " + meter.getMeterId() + " 缺少有效账期读数");
            List<PriceRule> rules = priceRuleRepository.findByMeterTypeOrderByTierAsc(meter.getType());
            if (rules.isEmpty()) throw new BusinessException(ErrorCode.PRICE_RULE_NOT_FOUND);
            BigDecimal usage = closing.subtract(opening);
            BigDecimal raw = billingService.waterFeeRaw(usage, rules);
            BigDecimal rate = rules.get(0).getPenaltyRate() == null ? new BigDecimal("0.0005") : rules.get(0).getPenaltyRate();
            BillItem item = BillItem.builder().batchNo(period + "-" + meter.getType().name()).period(period)
                    .meterId(meter.getMeterId()).userName(meter.getUserName()).area(meter.getArea()).meterType(meter.getType())
                    .usage(usage).openingReading(opening).closingReading(closing).rawWaterFee(raw).penaltyRate(rate)
                    .dueDate(deadline).waterFee(raw.setScale(2, RoundingMode.HALF_UP)).penalty(BigDecimal.ZERO)
                    .totalDue(raw.setScale(2, RoundingMode.HALF_UP)).paidAmount(BigDecimal.ZERO)
                    .overdueDays(0).status(DomainEnums.BillStatus.待缴).build();
            refresh(item);
            billItemRepository.save(item);
            notifications.notifyMeterOwner(meter.getMeterId(), "新账单已生成",
                    period + " 用水 " + usage.toPlainString() + " m³，应缴 " + item.getTotalDue() + " 元", "/user/home");
            created++;
        }
        refreshBatches();
        auditService.log(user.getUsername(), user.getRole().name(), "GENERATE_BILLS", period,
                RequestContext.getRequestId(), "SUCCESS", null, "created=" + created);
        return listBills(user).stream().filter(batch -> batch.batch().startsWith(period + "-")).toList();
    }

    private BigDecimal openingAt(Meter meter, YearMonth month) {
        Reading prior = readingRepository.findFirstByMeterIdAndTsBeforeOrderByTsDesc(
                meter.getMeterId(), month.atDay(1).atStartOfDay()).orElse(null);
        return prior == null ? meter.getLastReading() : prior.getCumulativeFlow();
    }

    private BigDecimal closingAt(Meter meter, YearMonth month) {
        if (month.equals(YearMonth.now())) return meter.getReading();
        Reading last = readingRepository.findFirstByMeterIdAndTsBeforeOrderByTsDesc(
                meter.getMeterId(), month.plusMonths(1).atDay(1).atStartOfDay()).orElse(null);
        return last == null ? null : last.getCumulativeFlow();
    }

    public List<BillItemDto> listItems(AuthUser user, String batch, String period) {
        List<BillItem> items = visibleItems(user);
        return items.stream().filter(i -> batch == null || batch.isBlank() || batch.equals(i.getBatchNo()))
                .filter(i -> period == null || period.isBlank() || period.equals(i.getPeriod()))
                .sorted(Comparator.comparing(BillItem::getId).reversed()).map(BillItemDto::from).toList();
    }

    public List<BillBatchDto> listBills(AuthUser user) {
        // Build aggregates from visible items, never expose global totals to a household/operator.
        Map<String, List<BillItem>> groups = new TreeMap<>(Comparator.reverseOrder());
        visibleItems(user).forEach(item -> groups.computeIfAbsent(item.getBatchNo(), k -> new ArrayList<>()).add(item));
        return groups.entrySet().stream().map(e -> BillBatchDto.from(batchFor(e.getKey(), e.getValue()))).toList();
    }

    /** Kept for internal callers; controllers always use the authenticated overload. */
    public List<BillBatchDto> listBills() {
        return listBills(com.zhishuiyun.water.security.CurrentUser.required());
    }

    public List<PriceRule> rules() {
        return priceRuleRepository.findAll();
    }

    private List<BillItem> visibleItems(AuthUser user) {
        List<BillItem> result = billItemRepository.findAll().stream()
                .filter(item -> accessPolicy.canAccessMeterId(user, item.getMeterId())).toList();
        result.forEach(this::refresh);
        return result;
    }

    public List<PaymentDto> payments(AuthUser user) {
        return paymentRepository.findAllByOrderByIdDesc().stream()
                .filter(payment -> accessPolicy.canAccessMeterId(user, payment.getMeterId()))
                .map(PaymentDto::from).toList();
    }

    public PaymentDto pay(Long id, String idempotencyKey, AuthUser user) {
        if (!user.isUser()) accessPolicy.requireBilling(user);
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9_-]{8,100}"))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请提供 8–100 位付款幂等键");
        BillItem item = billItemRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!accessPolicy.canAccessMeterId(user, item.getMeterId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        Payment duplicate = paymentRepository.findByPayerAndIdempotencyKey(user.getUsername(), idempotencyKey).orElse(null);
        if (duplicate != null) {
            if (!duplicate.getBillItemId().equals(id)) throw new BusinessException(ErrorCode.BAD_REQUEST, "付款幂等键已用于另一账单");
            return PaymentDto.from(duplicate);
        }
        Payment paid = paymentRepository.findByBillItemId(id).orElse(null);
        if (paid != null) return PaymentDto.from(paid); // A retry with a fresh browser key still cannot pay twice.
        refresh(item);
        BigDecimal amount = item.getTotalDue().subtract(zero(item.getPaidAmount())).max(BigDecimal.ZERO);
        if (amount.signum() == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "账单无待缴金额");
        // Server owns the payable amount. No submitted amount/account balance is trusted.
        Payment payment = paymentRepository.save(Payment.builder().transactionNo("SIM-" + UUID.randomUUID())
                .billItemId(id).meterId(item.getMeterId()).payer(user.getUsername()).idempotencyKey(idempotencyKey)
                .amount(amount).paidAt(LocalDateTime.now()).channel("SIMULATED").build());
        String old = item.getStatus().name();
        item.setPaidAmount(item.getTotalDue()); item.setPaidAt(payment.getPaidAt()); item.setStatus(DomainEnums.BillStatus.已缴);
        billItemRepository.save(item);
        refreshBatches();
        auditService.log(user.getUsername(), user.getRole().name(), "SIMULATED_PAYMENT", String.valueOf(id),
                RequestContext.getRequestId(), "SUCCESS", old, "已缴:" + amount);
        notifications.notifyMeterOwner(item.getMeterId(), "模拟缴费成功", item.getPeriod() + " 账单已缴清，金额 " + amount + " 元（演示交易）", "/user/home");
        notifications.notifyAdministrators("模拟缴费到账", item.getMeterId() + " 账单 " + id + " 收款 " + amount + " 元", "/admin/billing");
        return PaymentDto.from(payment);
    }

    /** Real overdue days, frozen after full payment; legacy rows receive a safe default deadline. */
    private void refresh(BillItem item) {
        if (item.getStatus() == DomainEnums.BillStatus.已缴) return;
        if (item.getDueDate() == null) item.setDueDate(YearMonth.parse(item.getPeriod()).atEndOfMonth().plusDays(15));
        long elapsed = Math.max(0, ChronoUnit.DAYS.between(item.getDueDate(), LocalDate.now()));
        int days = (int) Math.min(elapsed, Integer.MAX_VALUE);
        BigDecimal raw = item.getRawWaterFee() == null ? zero(item.getWaterFee()) : item.getRawWaterFee();
        BigDecimal rate = item.getPenaltyRate() == null ? new BigDecimal("0.0005") : item.getPenaltyRate();
        BigDecimal penaltyRaw = raw.multiply(rate).multiply(BigDecimal.valueOf(days));
        item.setOverdueDays(days);
        item.setPenalty(penaltyRaw.setScale(2, RoundingMode.HALF_UP));
        item.setTotalDue(raw.add(penaltyRaw).setScale(2, RoundingMode.HALF_UP));
        item.setStatus(item.getTotalDue().compareTo(zero(item.getPaidAmount())) <= 0 ? DomainEnums.BillStatus.已缴
                : days > 0 ? DomainEnums.BillStatus.逾期 : DomainEnums.BillStatus.待缴);
    }

    /** Other summary services can calculate the exact current payable amount without mutating DB. */
    public static BigDecimal currentTotalDue(BillItem item) {
        if (item.getStatus() == DomainEnums.BillStatus.已缴) return item.getTotalDue();
        LocalDate deadline = item.getDueDate() == null
                ? YearMonth.parse(item.getPeriod()).atEndOfMonth().plusDays(15) : item.getDueDate();
        long days = Math.max(0, ChronoUnit.DAYS.between(deadline, LocalDate.now()));
        BigDecimal raw = item.getRawWaterFee() == null ? item.getWaterFee() : item.getRawWaterFee();
        BigDecimal rate = item.getPenaltyRate() == null ? new BigDecimal("0.0005") : item.getPenaltyRate();
        return raw.add(raw.multiply(rate).multiply(BigDecimal.valueOf(days))).setScale(2, RoundingMode.HALF_UP);
    }

    /** Keep dashboard/AI repository-backed aggregates in sync even if nobody opens the bills screen. */
    @Scheduled(fixedDelay = 60000, initialDelay = 10000)
    public void refreshOverdueBills() { refreshBatches(); }

    private void refreshBatches() {
        Map<String, List<BillItem>> groups = new HashMap<>();
        billItemRepository.findAll().forEach(item -> { refresh(item); groups.computeIfAbsent(item.getBatchNo(), k -> new ArrayList<>()).add(item); });
        groups.forEach((number, items) -> {
            Bill calculated = batchFor(number, items);
            Bill batch = billRepository.findByBatchNo(number).orElse(calculated);
            batch.setUserCount(calculated.getUserCount()); batch.setTotalAmount(calculated.getTotalAmount());
            batch.setTotalPaid(calculated.getTotalPaid()); batch.setStatus(calculated.getStatus());
            billRepository.save(batch);
        });
    }

    private Bill batchFor(String number, List<BillItem> items) {
        BigDecimal due = items.stream().map(BillItem::getTotalDue).map(this::zero).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = items.stream().map(BillItem::getPaidAmount).map(this::zero).reduce(BigDecimal.ZERO, BigDecimal::add);
        return Bill.builder().batchNo(number).period(items.get(0).getPeriod()).meterType(items.get(0).getMeterType())
                .userCount(items.size()).totalAmount(due).totalPaid(paid).status(paid.compareTo(due) >= 0 ? "已归档" : "收费中")
                .createdAt(LocalDateTime.now()).build();
    }

    private BigDecimal zero(BigDecimal amount) { return amount == null ? BigDecimal.ZERO : amount; }
}
