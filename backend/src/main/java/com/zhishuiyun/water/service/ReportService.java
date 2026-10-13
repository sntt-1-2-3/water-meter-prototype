package com.zhishuiyun.water.service;

import com.zhishuiyun.water.common.BusinessException;
import com.zhishuiyun.water.common.ErrorCode;
import com.zhishuiyun.water.common.RequestContext;
import com.zhishuiyun.water.dto.ReportRequest;
import com.zhishuiyun.water.entity.DomainEnums;
import com.zhishuiyun.water.entity.Meter;
import com.zhishuiyun.water.entity.Reading;
import com.zhishuiyun.water.repository.MeterRepository;
import com.zhishuiyun.water.repository.ReadingRepository;
import com.zhishuiyun.water.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 原始上报数据入库 + 数据质量（DQ）校验，对应 DQ-001 ~ DQ-025。
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Pattern METER_ID_PATTERN = Pattern.compile("^[ABC]-\\d{5}$");
    private static final Set<String> VALID_ALARM_CODES = Set.of("0", "E01", "E02", "E03", "E04", "E05", "E06");
    private static final BigDecimal TEMP_MIN = new BigDecimal("0");
    private static final BigDecimal TEMP_MAX = new BigDecimal("60");

    private final ReadingRepository readingRepository;
    private final MeterRepository meterRepository;
    private final AuditService auditService;
    private final AccessPolicy accessPolicy;
    private final AnomalyService anomalyService;

    @Transactional
    public void report(ReportRequest req, AuthUser user) {
        accessPolicy.requireReadings(user);
        // 1. 表具编号
        if (isBlank(req.meterId())) {
            throw new BusinessException(ErrorCode.METER_ID_REQUIRED);
        }
        if (!METER_ID_PATTERN.matcher(req.meterId()).matches()) {
            throw new BusinessException(ErrorCode.METER_ID_FORMAT_INVALID);
        }
        Meter meter = meterRepository.findByMeterId(req.meterId())
                .orElseThrow(() -> new BusinessException(ErrorCode.METER_NOT_FOUND));
        accessPolicy.requireAccessMeter(user, meter);

        // 2. 时间戳
        LocalDateTime ts = parseTs(req.ts());
        if (ts.isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new BusinessException(ErrorCode.TIMESTAMP_OUT_OF_RANGE);
        }
        if (meter.getInstallTime() != null && ts.isBefore(meter.getInstallTime())) {
            throw new BusinessException(ErrorCode.TIMESTAMP_BEFORE_INSTALL);
        }

        // 3. 必填与数值范围
        if (req.instantFlow() == null) {
            throw new BusinessException(ErrorCode.INSTANT_FLOW_REQUIRED);
        }
        if (req.cumulativeFlow() == null) {
            throw new BusinessException(ErrorCode.CUMULATIVE_FLOW_REQUIRED);
        }
        if (req.temperature() == null) {
            throw new BusinessException(ErrorCode.TEMPERATURE_REQUIRED);
        }
        if (req.instantFlow().signum() < 0) {
            throw new BusinessException(ErrorCode.INSTANT_FLOW_NEGATIVE);
        }
        if (req.cumulativeFlow().signum() < 0) {
            throw new BusinessException(ErrorCode.CUMULATIVE_FLOW_NEGATIVE);
        }
        if (req.temperature().compareTo(TEMP_MIN) < 0 || req.temperature().compareTo(TEMP_MAX) > 0) {
            throw new BusinessException(ErrorCode.TEMPERATURE_OUT_OF_RANGE);
        }

        // 4. 阀门与报警码
        DomainEnums.ValveStatus valve = parseValve(req.valve());
        if (isBlank(req.alarmCode()) || !VALID_ALARM_CODES.contains(req.alarmCode())) {
            throw new BusinessException(ErrorCode.ALARM_CODE_INVALID);
        }
        if (valve == DomainEnums.ValveStatus.开启 && "E03".equals(req.alarmCode())) {
            throw new BusinessException(ErrorCode.ALARM_VALVE_CONFLICT);
        }

        // 5. 累计流量倒退
        Reading latest = readingRepository.findFirstByMeterIdOrderByTsDesc(req.meterId()).orElse(null);
        if (latest != null && ts.isBefore(latest.getTs())) {
            throw new BusinessException(ErrorCode.TIMESTAMP_CONFLICT, "不能用过时上报覆盖最新读数");
        }
        if (latest != null && latest.getCumulativeFlow() != null
                && req.cumulativeFlow().compareTo(latest.getCumulativeFlow()) < 0) {
            throw new BusinessException(ErrorCode.CUMULATIVE_ROLLBACK);
        }

        // 6. 重复 / 高频上报
        if (readingRepository.existsByMeterIdAndTs(req.meterId(), ts)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REPORT);
        }
        List<Reading> recent = readingRepository.findByMeterIdAndTsBetweenOrderByTsAsc(
                req.meterId(), ts.minusMinutes(1), ts);
        if (!recent.isEmpty()) {
            throw new BusinessException(ErrorCode.REPORT_FREQUENCY_HIGH);
        }

        // 7. 瞬时流量与累计增量一致性
        if (latest != null && latest.getCumulativeFlow() != null) {
            BigDecimal delta = req.cumulativeFlow().subtract(latest.getCumulativeFlow());
            if (req.instantFlow().compareTo(delta.max(BigDecimal.ZERO).add(BigDecimal.ONE)) > 0) {
                throw new BusinessException(ErrorCode.FLOW_INCONSISTENT);
            }
        }

        // 8. 入库
        Reading reading = Reading.builder()
                .meterId(req.meterId())
                .ts(ts)
                .instantFlow(req.instantFlow())
                .cumulativeFlow(req.cumulativeFlow())
                .temperature(req.temperature())
                .valve(valve)
                .alarmCode(req.alarmCode())
                .build();
        readingRepository.save(reading);
        anomalyService.detect(reading, meter);
        meter.setReading(req.cumulativeFlow());
        meter.setUpdatedAt(ts);
        meter.setStatus("E04".equals(req.alarmCode()) ? DomainEnums.MeterStatus.离线
                : anomalyService.hasUnresolved(meter.getMeterId()) ? DomainEnums.MeterStatus.异常 : DomainEnums.MeterStatus.在线);
        meterRepository.save(meter);
        auditService.log(user.getUsername(), user.getRole().name(), "READING_REPORT", req.meterId(),
                RequestContext.getRequestId(), "SUCCESS", null, null);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Explicitly labelled simulator input. Generated samples are monotonic, nonnegative and valid.
     * Device report rate limiting is deliberately not applied to accelerated demonstration sampling.
     * The caller must own a transaction (task worker or ReadingService.simulate).
     */
    public void collectSimulated(String meterId, String scenario, AuthUser user) {
        accessPolicy.requireReadings(user);
        Meter meter = meterRepository.findByMeterId(meterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.METER_NOT_FOUND));
        accessPolicy.requireAccessMeter(user, meter);
        Reading latest = readingRepository.findFirstByMeterIdOrderByTsDesc(meterId).orElse(null);
        LocalDateTime ts = LocalDateTime.now();
        if (latest != null && ts.isBefore(latest.getTs().plusSeconds(1))) ts = latest.getTs().plusSeconds(1);
        BigDecimal flow = "high_flow".equals(scenario) ? switch (meter.getType()) {
            case 居民 -> new BigDecimal("8"); case 商业 -> new BigDecimal("30"); case 工业 -> new BigDecimal("120");
        } : new BigDecimal("0.8");
        BigDecimal baseline = meter.getReading() == null ? BigDecimal.ZERO : meter.getReading();
        if (latest != null && latest.getCumulativeFlow() != null) baseline = baseline.max(latest.getCumulativeFlow());
        BigDecimal cumulative = baseline.add(flow.max(BigDecimal.ONE)).setScale(3, java.math.RoundingMode.HALF_UP);
        Reading reading = Reading.builder().meterId(meterId).ts(ts).instantFlow(flow).cumulativeFlow(cumulative)
                .temperature(new BigDecimal("22.5")).valve(DomainEnums.ValveStatus.开启)
                .alarmCode("alarm".equals(scenario) ? "E01" : "0").build();
        readingRepository.save(reading);
        meter.setReading(cumulative); meter.setUpdatedAt(ts);
        anomalyService.detect(reading, meter);
        meter.setStatus(anomalyService.hasUnresolved(meterId) ? DomainEnums.MeterStatus.异常 : DomainEnums.MeterStatus.在线);
        meterRepository.save(meter);
        auditService.log(user.getUsername(), user.getRole().name(), "SIMULATED_READING", meterId,
                RequestContext.getRequestId(), "SUCCESS", baseline.toPlainString(), cumulative.toPlainString());
    }

    private LocalDateTime parseTs(String ts) {
        if (isBlank(ts)) {
            throw new BusinessException(ErrorCode.TIMESTAMP_REQUIRED);
        }
        String trimmed = ts.trim();
        try {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (java.time.format.DateTimeParseException ignored) {
            // Also accept the device's documented space-separated timestamp format.
        }
        for (DateTimeFormatter fmt : List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))) {
            try {
                return LocalDateTime.parse(trimmed, fmt);
            } catch (Exception ignored) {
                // try next
            }
        }
        throw new BusinessException(ErrorCode.TIMESTAMP_INVALID);
    }

    private DomainEnums.ValveStatus parseValve(String valve) {
        if (isBlank(valve)) {
            throw new BusinessException(ErrorCode.VALVE_STATUS_INVALID);
        }
        try {
            return DomainEnums.ValveStatus.valueOf(valve);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALVE_STATUS_INVALID);
        }
    }
}
