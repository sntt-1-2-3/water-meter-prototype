package com.zhishuiyun.water.service;

import com.zhishuiyun.water.common.*;
import com.zhishuiyun.water.dto.AnomalyDto;
import com.zhishuiyun.water.entity.*;
import com.zhishuiyun.water.repository.*;
import com.zhishuiyun.water.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class AnomalyService {
    private final AnomalyRepository anomalyRepository;
    private final MeterRepository meterRepository;
    private final ReadingRepository readingRepository;
    private final AuditService auditService;
    private final AccessPolicy accessPolicy;
    private final NotificationService notifications;

    @Transactional(readOnly = true)
    public List<AnomalyDto> list(String level, AuthUser user) {
        DomainEnums.RiskLevel filter = null;
        if (level != null && !level.isBlank() && !"全部".equals(level)) {
            try { filter = DomainEnums.RiskLevel.valueOf(level); }
            catch (Exception e) { throw new BusinessException(ErrorCode.BAD_REQUEST, "风险等级无效"); }
        }
        final DomainEnums.RiskLevel selected = filter;
        return anomalyRepository.findAllByOrderByIdDesc().stream()
                .filter(a -> selected == null || a.getLevel() == selected)
                .filter(a -> accessPolicy.canAccessMeterId(user, a.getMeterId()))
                .map(AnomalyDto::from).toList();
    }

    public AnomalyDto updateStatus(String anomalyId, String status, AuthUser user) {
        accessPolicy.requireManageAnomalies(user);
        Anomaly anomaly = anomalyRepository.findByAnomalyId(anomalyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!accessPolicy.canAccessMeterId(user, anomaly.getMeterId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        DomainEnums.AnomalyStatus target;
        try { target = DomainEnums.AnomalyStatus.valueOf(status); }
        catch (Exception e) { throw new BusinessException(ErrorCode.BAD_REQUEST, "异常处理状态无效"); }
        DomainEnums.AnomalyStatus old = anomaly.getStatus();
        if (old == target) return AnomalyDto.from(anomaly);
        if (!((old == DomainEnums.AnomalyStatus.待处理 && target == DomainEnums.AnomalyStatus.处理中)
                || (old == DomainEnums.AnomalyStatus.处理中 && target == DomainEnums.AnomalyStatus.已完成)))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "异常必须依次从待处理、处理中到已完成");
        anomaly.setStatus(target); anomaly.setConfirmedBy(user.getUsername()); anomaly.setConfirmedAt(LocalDateTime.now());
        anomalyRepository.save(anomaly);
        auditService.log(user.getUsername(), user.getRole().name(), "UPDATE_ANOMALY", anomalyId,
                RequestContext.getRequestId(), "SUCCESS", old.name(), target.name());
        if (target == DomainEnums.AnomalyStatus.已完成) meterRepository.findByMeterId(anomaly.getMeterId()).ifPresent(meter -> {
            if (!hasUnresolved(meter.getMeterId()) && meter.getStatus() != DomainEnums.MeterStatus.离线) {
                meter.setStatus(DomainEnums.MeterStatus.在线); meterRepository.save(meter);
            }
        });
        notifications.notifyMeterOwner(anomaly.getMeterId(), "异常处置进度更新", anomaly.getType() + "：" + target.name(), "/user/home");
        return AnomalyDto.from(anomaly);
    }

    /** Called in the same ingestion transaction, so raw data and derived alarms are committed together. */
    public void detect(Reading reading, Meter meter) {
        String alarm = reading.getAlarmCode();
        if (alarm != null && !"0".equals(alarm)) {
            boolean offline = "E04".equals(alarm);
            create(meter, offline ? "水表离线" : "设备报警 " + alarm,
                    offline ? DomainEnums.RiskLevel.中 : DomainEnums.RiskLevel.高, alarm,
                    offline ? "检查通讯模块与供电，并重试补抄" : "核查设备报警及阀门状态", reading.getTs());
        }
        List<Reading> recent = readingRepository.findTop3ByMeterIdOrderByTsDesc(meter.getMeterId());
        BigDecimal limit = switch (meter.getType()) {
            case 居民 -> new BigDecimal("5");
            case 商业 -> new BigDecimal("20");
            case 工业 -> new BigDecimal("100");
        };
        if (recent.size() == 3 && recent.get(2).getTs().isAfter(reading.getTs().minusMinutes(10))
                && recent.stream().allMatch(r -> r.getInstantFlow() != null
                && r.getInstantFlow().compareTo(limit) > 0)) {
            create(meter, "持续高流量", DomainEnums.RiskLevel.高, "E01",
                    "连续三次采样超过 " + limit + " m³/h，请核查漏水或异常用水", reading.getTs());
        }
    }

    public void offline(Meter meter, LocalDateTime time) {
        create(meter, "水表离线", DomainEnums.RiskLevel.中, "E04", "超过 12 小时未上报，请检查通信及供电", time);
        meter.setStatus(DomainEnums.MeterStatus.离线);
        meterRepository.save(meter);
    }

    public boolean hasUnresolved(String meterId) {
        return anomalyRepository.findByMeterIdAndStatusNot(meterId, DomainEnums.AnomalyStatus.已完成).stream().findAny().isPresent();
    }

    private void create(Meter meter, String type, DomainEnums.RiskLevel level, String code, String suggestion, LocalDateTime time) {
        if (anomalyRepository.findByMeterIdAndStatusNot(meter.getMeterId(), DomainEnums.AnomalyStatus.已完成)
                .stream().anyMatch(a -> type.equals(a.getType()))) return;
        Anomaly anomaly = anomalyRepository.save(Anomaly.builder().anomalyId("AL-" + UUID.randomUUID())
                .meterId(meter.getMeterId()).area(meter.getArea()).type(type).level(level).time(time)
                .alarmCode(code).suggestion(suggestion).status(DomainEnums.AnomalyStatus.待处理).build());
        meter.setStatus(DomainEnums.MeterStatus.异常);
        meterRepository.save(meter);
        auditService.log("simulator", "SYSTEM", "DETECT_ANOMALY", anomaly.getAnomalyId(),
                RequestContext.getRequestId(), "SUCCESS", null, type);
        notifications.notifyAdministrators("水表异常预警", meter.getMeterId() + " " + type + "（" + level + "风险）", "/admin/anomalies");
        notifications.notifyMeterOwner(meter.getMeterId(), "用水异常提醒", type + "；" + suggestion, "/user/home");
    }
}
