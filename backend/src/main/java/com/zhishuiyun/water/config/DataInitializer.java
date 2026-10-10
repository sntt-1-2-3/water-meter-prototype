package com.zhishuiyun.water.config;

import com.zhishuiyun.water.entity.*;
import com.zhishuiyun.water.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 首次启动时写入种子数据：6 账号、水价规则、60 台水表、示例异常与上报数据。
 * 仅在对应表为空时执行，可重复启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AccountRepository accountRepository;
    private final PriceRuleRepository priceRuleRepository;
    private final MeterRepository meterRepository;
    private final AnomalyRepository anomalyRepository;
    private final ReadingRepository readingRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String DEFAULT_PASSWORD = "admin123";

    @Override
    @Transactional
    public void run(String... args) {
        initAccounts();
        initPriceRules();
        initMeters();
        initAnomalies();
        initReadings();
        log.info("种子数据初始化完成：账号 {}, 水价规则 {}, 水表 {}, 异常 {}, 上报 {}",
                accountRepository.count(), priceRuleRepository.count(), meterRepository.count(),
                anomalyRepository.count(), readingRepository.count());
    }

    private void initAccounts() {
        if (accountRepository.count() > 0) {
            return;
        }
        accountRepository.saveAll(List.of(
                account("admin", "系统管理员", DomainEnums.AccountRole.ADMIN, null, null, true),
                account("operator_east", "东区运营员", DomainEnums.AccountRole.OPERATOR,
                        "滨江花园,江南新城", null, true),
                account("finance01", "财务专员", DomainEnums.AccountRole.FINANCE, null, null, true),
                account("auditor01", "审计员", DomainEnums.AccountRole.AUDITOR, null, null, true),
                account("user_res_001", "张明远", DomainEnums.AccountRole.USER, null, "A-00218", true),
                account("user_com_001", "青禾餐饮", DomainEnums.AccountRole.USER, null, "B-00126", false)));
    }

    private Account account(String username, String name, DomainEnums.AccountRole role,
                            String areaScope, String boundMeterId, boolean enabled) {
        return Account.builder()
                .username(username)
                .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                .name(name)
                .role(role)
                .areaScope(areaScope)
                .boundMeterId(boundMeterId)
                .enabled(enabled)
                .build();
    }

    private void initPriceRules() {
        if (priceRuleRepository.count() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<PriceRule> rules = new ArrayList<>();
        // 居民阶梯
        rules.add(rule(1, DomainEnums.MeterType.居民, 1, "0", "12", "2.80", now));
        rules.add(rule(1, DomainEnums.MeterType.居民, 2, "12", "20", "3.60", now));
        rules.add(rule(1, DomainEnums.MeterType.居民, 3, "20", null, "5.20", now));
        // 商业统一价
        rules.add(rule(1, DomainEnums.MeterType.商业, 1, "0", null, "4.20", now));
        // 工业合同价
        rules.add(rule(1, DomainEnums.MeterType.工业, 1, "0", null, "5.10", now));
        priceRuleRepository.saveAll(rules);
    }

    private PriceRule rule(int version, DomainEnums.MeterType type, int tier,
                           String from, String to, String price, LocalDateTime now) {
        return PriceRule.builder()
                .version(version)
                .meterType(type)
                .tier(tier)
                .fromUsage(new BigDecimal(from))
                .toUsage(to == null ? null : new BigDecimal(to))
                .unitPrice(new BigDecimal(price))
                .penaltyRate(new BigDecimal("0.0005"))
                .effectiveFrom(now)
                .build();
    }

    private void initMeters() {
        if (meterRepository.count() > 0) {
            return;
        }
        List<Meter> meters = new ArrayList<>();
        LocalDateTime install = LocalDate.now().minusYears(1).atStartOfDay();

        String[] residentAreas = {"滨江花园", "江南新城", "锦绣家园"};
        for (int i = 0; i < 30; i++) {
            String id = String.format("A-%05d", 218 + i);
            BigDecimal last = BigDecimal.valueOf(1000 + i * 15L);
            BigDecimal usage = BigDecimal.valueOf(150 + (i % 10) * 10L).divide(BigDecimal.TEN); // 15.0 ~ 24.0
            meters.add(meter(id, i == 0 ? "张明远" : "居民用户" + (i + 1),
                    residentAreas[i % 3], DomainEnums.MeterType.居民, last, last.add(usage), install));
        }
        String[] comAreas = {"中央商务区", "滨江花园商圈", "高新服务中心"};
        for (int i = 0; i < 18; i++) {
            String id = String.format("B-%05d", 126 + i);
            BigDecimal last = BigDecimal.valueOf(2000 + i * 10L);
            BigDecimal usage = BigDecimal.valueOf(800 + (i % 80) * 10L).divide(BigDecimal.TEN); // 80.0 ~ 159.0
            meters.add(meter(id, i == 0 ? "青禾餐饮" : "商业用户" + (i + 1),
                    comAreas[i % 3], DomainEnums.MeterType.商业, last, last.add(usage), install));
        }
        String[] indAreas = {"高新工业园", "临港产业园"};
        for (int i = 0; i < 12; i++) {
            String id = String.format("C-%05d", 37 + i);
            BigDecimal last = BigDecimal.valueOf(2500 + i * 20L);
            BigDecimal usage = BigDecimal.valueOf(550 + (i % 50) * 10L); // 550 ~ 1040
            meters.add(meter(id, "工业企业" + (i + 1), indAreas[i % 2],
                    DomainEnums.MeterType.工业, last, last.add(usage), install));
        }
        meterRepository.saveAll(meters);
    }

    private Meter meter(String id, String userName, String area, DomainEnums.MeterType type,
                        BigDecimal last, BigDecimal reading, LocalDateTime install) {
        return Meter.builder()
                .meterId(id)
                .userName(userName)
                .area(area)
                .type(type)
                .reading(reading)
                .lastReading(last)
                .status(DomainEnums.MeterStatus.在线)
                .installTime(install)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void initAnomalies() {
        if (anomalyRepository.count() > 0) {
            return;
        }
        LocalDateTime base = LocalDate.now().minusDays(2).atTime(LocalTime.of(8, 0));
        anomalyRepository.saveAll(List.of(
                anomaly("AL-2609-001", "A-00236", "滨江花园", "持续高流量",
                        DomainEnums.RiskLevel.高, "E01", "立即核查是否存在漏水或异常用水", DomainEnums.AnomalyStatus.待处理, base),
                anomaly("AL-2609-002", "A-00245", "滨江花园", "夜间小流量",
                        DomainEnums.RiskLevel.中, "E02", "电话核查住户夜间用水习惯", DomainEnums.AnomalyStatus.待处理, base.minusHours(1)),
                anomaly("AL-2609-004", "A-00240", "江南新城", "累计流量倒退",
                        DomainEnums.RiskLevel.高, "E05", "检查表计和数据校验逻辑", DomainEnums.AnomalyStatus.处理中, base.minusHours(2)),
                anomaly("AL-2609-005", "B-00128", "高新服务中心", "阀门故障 E03",
                        DomainEnums.RiskLevel.高, "E03", "生成阀门故障工单", DomainEnums.AnomalyStatus.待处理, base.minusHours(3)),
                anomaly("AL-2609-008", "C-00041", "高新工业园", "水表离线",
                        DomainEnums.RiskLevel.中, "E04", "检查通信模块和网络", DomainEnums.AnomalyStatus.待处理, base.minusHours(4)),
                anomaly("AL-2609-011", "A-00223", "锦绣家园", "短时离线",
                        DomainEnums.RiskLevel.低, "E04", "等待自动补采", DomainEnums.AnomalyStatus.已完成, base.minusDays(1))));
    }

    private Anomaly anomaly(String id, String meterId, String area, String type,
                            DomainEnums.RiskLevel level, String alarmCode, String suggestion,
                            DomainEnums.AnomalyStatus status, LocalDateTime time) {
        return Anomaly.builder()
                .anomalyId(id)
                .meterId(meterId)
                .area(area)
                .type(type)
                .level(level)
                .time(time)
                .alarmCode(alarmCode)
                .suggestion(suggestion)
                .status(status)
                .build();
    }

    /** 近 7 天每表每天一条上报，累计流量从 lastReading 均匀增长到 reading。 */
    private void initReadings() {
        if (readingRepository.count() > 0) {
            return;
        }
        List<Reading> readings = new ArrayList<>();
        List<Meter> meters = meterRepository.findAll();
        LocalDate today = LocalDate.now();
        for (Meter m : meters) {
            BigDecimal last = m.getLastReading();
            BigDecimal current = m.getReading();
            BigDecimal delta = current.subtract(last);
            BigDecimal dailyStep = delta.divide(BigDecimal.valueOf(7), 4, java.math.RoundingMode.HALF_UP);
            BigDecimal cumulative = last;
            for (int d = 0; d < 7; d++) {
                cumulative = cumulative.add(dailyStep);
                readings.add(Reading.builder()
                        .meterId(m.getMeterId())
                        .ts(today.minusDays(6 - d).atTime(LocalTime.of(9, 0)))
                        .instantFlow(dailyStep)
                        .cumulativeFlow(cumulative.setScale(2, java.math.RoundingMode.HALF_UP))
                        .temperature(new BigDecimal("22.1"))
                        .valve(DomainEnums.ValveStatus.开启)
                        .alarmCode("0")
                        .build());
            }
        }
        readingRepository.saveAll(readings);
    }
}
