package com.zhishuiyun.water;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishuiyun.water.entity.Account;
import com.zhishuiyun.water.entity.DomainEnums;
import com.zhishuiyun.water.entity.Meter;
import com.zhishuiyun.water.repository.AccountRepository;
import com.zhishuiyun.water.repository.MeterRepository;
import com.zhishuiyun.water.repository.ReadingRepository;
import com.zhishuiyun.water.service.NotificationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/** Exercises real HTTP controllers, JWT filters and an isolated persistent SQLite database. */
@SpringBootTest(properties = {
        "simulator.enabled=false",
        "deepseek.enabled=false",
        "jwt.secret=integration-test-only-signing-secret-never-for-deployment-0123456789",
        "spring.jpa.hibernate.ddl-auto=create"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiIntegrationTest {
    private static final String RUN_ID = UUID.randomUUID().toString().replace("-", "");
    private static final AtomicInteger FIXTURE_ID = new AtomicInteger(90000);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        Path directory = Path.of("target", "integration-tests").toAbsolutePath();
        Files.createDirectories(directory);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + directory.resolve(RUN_ID + ".db"));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountRepository accounts;
    @Autowired MeterRepository meters;
    @Autowired ReadingRepository readings;
    @Autowired NotificationService notifications;

    private String admin;
    private String resident;
    private String operator;
    private String finance;
    private String auditor;

    @BeforeAll
    void authenticateFixtures() throws Exception {
        admin = login("admin");
        resident = login("user_res_001");
        operator = login("operator_east");
        finance = login("finance01");
        auditor = login("auditor01");
    }

    @Test
    void businessEndpointsRequireAuthentication() throws Exception {
        for (String path : List.of("/api/meters", "/api/dashboard", "/api/bills/items", "/api/notifications", "/api/feedback")) {
            exchange("GET", path, null, null, 401);
        }
        exchange("GET", "/api/auth/me", null, "not-a-valid-jwt", 401);
    }

    @Test
    void invalidAndDisabledAccountsCannotLogin() throws Exception {
        exchange("POST", "/api/auth/login", Map.of("username", "admin", "password", "wrong-password"), null, 401);
        exchange("POST", "/api/auth/login", Map.of("username", "user_com_001", "password", "admin123"), null, 403);
    }

    @Test
    void loginReturnsCurrentServerProfileWithoutPassword() throws Exception {
        JsonNode profile = data("GET", "/api/auth/me", null, resident);
        assertThat(profile.path("role").asText()).isEqualTo("USER");
        assertThat(profile.path("boundMeterId").asText()).isEqualTo("A-00218");
        assertThat(profile.has("password")).isFalse();
        JsonNode list = data("GET", "/api/accounts", null, admin);
        for (JsonNode account : list) assertThat(account.has("password")).isFalse();
    }

    @Test
    void registrationCannotEscalateRoleOrClaimExistingMeter() throws Exception {
        String name = "public_" + FIXTURE_ID.incrementAndGet();
        JsonNode result = data("POST", "/api/auth/register", Map.of(
                "username", name, "password", "secure1234", "name", "测试住户", "phone", "13800000000",
                "role", "ADMIN", "boundMeterId", "A-00218", "areaScope", "全部"), null);
        assertThat(result.path("role").asText()).isEqualTo("USER");
        assertThat(result.path("boundMeterId").isMissingNode() || result.path("boundMeterId").isNull()
                || result.path("boundMeterId").asText().isEmpty()).isTrue();
        Account stored = accounts.findByUsername(name).orElseThrow();
        assertThat(stored.getPassword()).startsWith("$2").isNotEqualTo("secure1234");
        assertThat(stored.getRole()).isEqualTo(DomainEnums.AccountRole.USER);
    }

    @Test
    void duplicateRegistrationIsRejectedWithoutReplacingPassword() throws Exception {
        String name = "duplicate_" + FIXTURE_ID.incrementAndGet();
        Map<String, String> body = Map.of("username", name, "password", "secure1234", "name", "测试住户");
        data("POST", "/api/auth/register", body, null);
        MvcResult rejected = raw("POST", "/api/auth/register", body, null);
        assertThat(rejected.getResponse().getStatus()).isIn(400, 409);
        assertThat(accounts.findByUsername(name).orElseThrow().getPassword()).startsWith("$2");
    }

    @Test
    void unboundNewUserDoesNotSeeGlobalData() throws Exception {
        JsonNode account = registerFixture();
        String token = account.path("token").asText();
        assertThat(data("GET", "/api/meters", null, token).path("total").asInt()).isZero();
        assertThat(data("GET", "/api/bills/items", null, token)).isEmpty();
        assertThat(data("GET", "/api/anomalies", null, token)).isEmpty();
        exchange("POST", "/api/feedback", Map.of("type", "疑似漏水", "description", "未绑定账户不能反馈他人水表"), token, 400);
    }

    @Test
    void residentOnlyReadsBoundMeter() throws Exception {
        JsonNode page = data("GET", "/api/meters?page=1&pageSize=100", null, resident);
        assertThat(page.path("total").asInt()).isEqualTo(1);
        assertThat(page.path("items").get(0).path("id").asText()).isEqualTo("A-00218");
    }

    @Test
    void residentCannotInvokeManagementEndpoints() throws Exception {
        exchange("GET", "/api/accounts", null, resident, 403);
        exchange("POST", "/api/meters", meterBody("A-99998", "滨江花园"), resident, 403);
        exchange("POST", "/api/readings/tasks", Map.of(), resident, 403);
        exchange("POST", "/api/readings/simulate", Map.of("meterId", "A-00218", "scenario", "alarm"), resident, 403);
        exchange("POST", "/api/bills/recalculate", Map.of(), resident, 403);
        exchange("PATCH", "/api/anomalies/AL-2609-001", Map.of("status", "处理中"), resident, 403);
    }

    @Test
    void operatorOnlySeesAndEditsAssignedAreas() throws Exception {
        JsonNode page = data("GET", "/api/meters?page=1&pageSize=200", null, operator);
        for (JsonNode meter : page.path("items")) {
            assertThat(meter.path("area").asText()).isIn("滨江花园", "江南新城");
        }
        exchange("PUT", "/api/meters/C-00037", meterBody("C-00037", "高新工业园"), operator, 403);
        exchange("POST", "/api/bills/recalculate", Map.of(), operator, 403);
        exchange("GET", "/api/accounts", null, operator, 403);
    }

    @Test
    void financeCannotMutateMeterOrCreateReadingTask() throws Exception {
        exchange("POST", "/api/meters", meterBody("A-99997", "滨江花园"), finance, 403);
        exchange("POST", "/api/readings/tasks", Map.of(), finance, 403);
        exchange("PATCH", "/api/anomalies/AL-2609-001", Map.of("status", "处理中"), finance, 403);
    }

    @Test
    void auditorIsReadOnly() throws Exception {
        data("GET", "/api/meters?page=1&pageSize=200", null, auditor);
        exchange("POST", "/api/readings/tasks", Map.of(), auditor, 403);
        exchange("POST", "/api/bills/recalculate", Map.of(), auditor, 403);
        exchange("PATCH", "/api/anomalies/AL-2609-001", Map.of("status", "处理中"), auditor, 403);
    }

    @Test
    void disablingAccountInvalidatesExistingToken() throws Exception {
        JsonNode fresh = registerFixture();
        String token = fresh.path("token").asText();
        Account account = accounts.findByUsername(fresh.path("username").asText()).orElseThrow();
        account.setEnabled(false);
        accounts.saveAndFlush(account);
        int status = raw("GET", "/api/auth/me", null, token).getResponse().getStatus();
        assertThat(status).isIn(401, 403);
    }

    @Test
    void administratorBindingIsEffectiveOnExistingToken() throws Exception {
        BoundFixture fixture = boundFixture("滨江花园");
        JsonNode profile = data("GET", "/api/auth/me", null, fixture.token());
        assertThat(profile.path("boundMeterId").asText()).isEqualTo(fixture.meterId());
        JsonNode page = data("GET", "/api/meters", null, fixture.token());
        assertThat(page.path("total").asInt()).isEqualTo(1);
        exchange("PATCH", "/api/accounts/" + fixture.accountId() + "/binding",
                Map.of("boundMeterId", "A-00218"), fixture.token(), 403);
    }

    @Test
    void anotherUserCannotReadOrMarkPrivateNotification() throws Exception {
        BoundFixture fixture = boundFixture("滨江花园");
        notifications.notifyUser(fixture.username(), "仅测试住户可见", "private-marker-" + fixture.username(), "/user/home");
        JsonNode own = data("GET", "/api/notifications", null, fixture.token());
        JsonNode notice = findBy(own, "title", "仅测试住户可见");
        exchange("GET", "/api/notifications/" + notice.path("id").asLong(), null, resident, 404);
        exchange("PATCH", "/api/notifications/" + notice.path("id").asLong() + "/read", Map.of(), resident, 404);
        data("PATCH", "/api/notifications/" + notice.path("id").asLong() + "/read", Map.of(), fixture.token());
        assertThat(findBy(data("GET", "/api/notifications", null, fixture.token()), "title", "仅测试住户可见")
                .path("read").asBoolean()).isTrue();
    }

    @Test
    void feedbackPersistsThroughProcessingAndGeneratesUserNotice() throws Exception {
        BoundFixture fixture = boundFixture("滨江花园");
        JsonNode feedback = data("POST", "/api/feedback", Map.of("type", "疑似漏水", "description", "管道持续流水，请检查"), fixture.token());
        long id = feedback.path("id").asLong();
        assertThat(id).isPositive();
        data("PATCH", "/api/feedback/" + id, Map.of("status", "处理中", "resolution", "已安排检查"), operator);
        data("PATCH", "/api/feedback/" + id, Map.of("status", "已完成", "resolution", "阀门已修复"), operator);
        JsonNode persisted = findBy(data("GET", "/api/feedback", null, fixture.token()), "id", String.valueOf(id));
        assertThat(persisted.path("status").asText()).isEqualTo("已完成");
        assertThat(persisted.path("resolution").asText()).isEqualTo("阀门已修复");
        assertThat(data("GET", "/api/notifications", null, fixture.token())).isNotEmpty();
        exchange("PATCH", "/api/feedback/" + id, Map.of("status", "已完成", "resolution", "自己完成"), fixture.token(), 403);
    }

    @Test
    void feedbackVisibilityAndProcessingRespectMeterScope() throws Exception {
        BoundFixture fixture = boundFixture("锦绣家园");
        JsonNode feedback = data("POST", "/api/feedback", Map.of("type", "设备损坏", "description", "测试独立区域反馈"), fixture.token());
        long id = feedback.path("id").asLong();
        for (JsonNode row : data("GET", "/api/feedback", null, operator)) {
            assertThat(row.path("id").asLong()).isNotEqualTo(id);
        }
        exchange("PATCH", "/api/feedback/" + id, Map.of("status", "处理中", "resolution", "越区处理"), operator, 403);
        for (JsonNode row : data("GET", "/api/feedback", null, resident)) {
            assertThat(row.path("id").asLong()).isNotEqualTo(id);
        }
    }

    @Test
    void readingTaskGeneratesFreshReadingsAndRetriesMissedMeter() throws Exception {
        long before = readings.count();
        JsonNode task = data("POST", "/api/readings/tasks", Map.of("simulateFailure", true), admin);
        String id = task.path("id").asText();
        JsonNode finished = awaitTask(id, admin);
        assertThat(finished.path("status").asText()).isEqualTo("已完成");
        assertThat(finished.path("success").asInt()).isEqualTo(finished.path("total").asInt());
        assertThat(finished.path("retryCount").asInt()).isPositive();
        assertThat(readings.count()).isGreaterThan(before);
    }

    @Test
    void simulatedAlarmProducesDeduplicatedPersistentAnomaly() throws Exception {
        BoundFixture fixture = boundFixture("滨江花园");
        Map<String, String> simulation = Map.of("meterId", fixture.meterId(), "scenario", "alarm");
        data("POST", "/api/readings/simulate", simulation, admin);
        data("POST", "/api/readings/simulate", simulation, admin);
        List<JsonNode> own = matching(data("GET", "/api/anomalies", null, fixture.token()), "meter", fixture.meterId());
        assertThat(own).hasSize(1);
        String id = own.get(0).path("id").asText();
        data("PATCH", "/api/anomalies/" + id, Map.of("status", "处理中"), operator);
        data("PATCH", "/api/anomalies/" + id, Map.of("status", "已完成"), operator);
        assertThat(findBy(data("GET", "/api/anomalies", null, fixture.token()), "id", id)
                .path("status").asText()).isEqualTo("已完成");
    }

    @Test
    void repeatedHighFlowCreatesAnomalyAutomatically() throws Exception {
        BoundFixture fixture = boundFixture("滨江花园");
        data("POST", "/api/readings/simulate", Map.of("meterId", fixture.meterId(), "scenario", "high_flow"), admin);
        assertThat(matching(data("GET", "/api/anomalies", null, fixture.token()), "meter", fixture.meterId())).isNotEmpty();
    }

    @Test
    void readingSchedulerSwitchIsAdministratorOnly() throws Exception {
        data("PATCH", "/api/readings/scheduler", Map.of("enabled", false), admin);
        JsonNode initial = data("GET", "/api/readings/scheduler", null, admin);
        assertThat(initial.path("enabled").asBoolean()).isFalse();
        assertThat(initial.path("intervalSeconds").asInt()).isGreaterThanOrEqualTo(10);
        for (String token : List.of(resident, operator, finance, auditor)) {
            exchange("GET", "/api/readings/scheduler", null, token, 403);
            exchange("PATCH", "/api/readings/scheduler", Map.of("enabled", true), token, 403);
        }
        exchange("GET", "/api/readings/scheduler", null, null, 401);
        try {
            assertThat(data("PATCH", "/api/readings/scheduler", Map.of("enabled", true), admin)
                    .path("enabled").asBoolean()).isTrue();
            assertThat(data("GET", "/api/readings/scheduler", null, admin)
                    .path("enabled").asBoolean()).isTrue();
        } finally {
            data("PATCH", "/api/readings/scheduler", Map.of("enabled", false), admin);
        }
        assertThat(data("GET", "/api/readings/scheduler", null, admin).path("enabled").asBoolean()).isFalse();
    }

    @Test
    void residentAnomalyListIsPrivateAndReadOnly() throws Exception {
        BoundFixture owner = boundFixture("滨江花园");
        BoundFixture stranger = boundFixture("江南新城");
        data("POST", "/api/readings/simulate", Map.of("meterId", owner.meterId(), "scenario", "alarm"), admin);
        data("POST", "/api/readings/simulate", Map.of("meterId", stranger.meterId(), "scenario", "alarm"), admin);
        JsonNode visible = data("GET", "/api/anomalies", null, owner.token());
        assertThat(visible).isNotEmpty();
        for (JsonNode anomaly : visible) assertThat(anomaly.path("meter").asText()).isEqualTo(owner.meterId());
        assertThat(matching(visible, "meter", stranger.meterId())).isEmpty();
        String ownId = visible.get(0).path("id").asText();
        String foreignId = findBy(data("GET", "/api/anomalies", null, stranger.token()), "meter", stranger.meterId())
                .path("id").asText();
        exchange("PATCH", "/api/anomalies/" + ownId, Map.of("status", "处理中"), owner.token(), 403);
        exchange("PATCH", "/api/anomalies/" + foreignId, Map.of("status", "处理中"), owner.token(), 403);
        assertThat(data("GET", "/api/anomalies", null, registerFixture().path("token").asText())).isEmpty();
    }

    @Test
    void normalReadingDoesNotEraseUnresolvedAlarm() throws Exception {
        BoundFixture fixture = boundFixture("滨江花园");
        data("POST", "/api/readings/simulate", Map.of("meterId", fixture.meterId(), "scenario", "alarm"), admin);
        JsonNode original = findBy(data("GET", "/api/anomalies", null, fixture.token()), "meter", fixture.meterId());
        data("POST", "/api/readings/simulate", Map.of("meterId", fixture.meterId(), "scenario", "normal"), admin);
        JsonNode persisted = findBy(data("GET", "/api/anomalies", null, fixture.token()), "id", original.path("id").asText());
        assertThat(persisted.path("status").asText()).isEqualTo("待处理");
        JsonNode device = data("GET", "/api/meters", null, fixture.token()).path("items").get(0);
        assertThat(device.path("status").asText()).isEqualTo("异常");
    }

    @Test
    void disablingTaskOwnerStopsBackgroundWritesAndAdministratorCanRecover() throws Exception {
        stopAndDrainReadingTasks();
        OperatorFixture fixture = operatorFixture("撤销测试区-" + FIXTURE_ID.incrementAndGet(), 12);
        JsonNode started = data("POST", "/api/readings/tasks", Map.of(), fixture.token());
        Account account = accounts.findByUsername(fixture.username()).orElseThrow();
        account.setEnabled(false);
        accounts.saveAndFlush(account);
        long afterRevocation = countReadings(fixture.meterIds());
        JsonNode stopped = awaitTask(started.path("id").asText(), admin);
        assertThat(stopped.path("status").asText()).isEqualTo("失败待重试");
        assertThat(stopped.path("success").asInt()).isLessThan(stopped.path("total").asInt());
        assertThat(stopped.path("failed").asInt()).isPositive();
        assertThat(countReadings(fixture.meterIds())).isEqualTo(afterRevocation);
        int rejected = raw("GET", "/api/readings/tasks", null, fixture.token()).getResponse().getStatus();
        assertThat(rejected).isIn(401, 403);
        data("POST", "/api/readings/tasks/" + started.path("id").asText() + "/retry", Map.of(), admin);
        JsonNode completed = awaitTask(started.path("id").asText(), admin);
        assertThat(completed.path("status").asText()).isEqualTo("已完成");
        assertThat(completed.path("success").asInt()).isEqualTo(12);
        assertThat(countReadings(fixture.meterIds())).isEqualTo(12);
    }

    @Test
    void changingOperatorScopeStopsQueuedOutOfScopeCollection() throws Exception {
        stopAndDrainReadingTasks();
        OperatorFixture fixture = operatorFixture("旧辖区-" + FIXTURE_ID.incrementAndGet(), 12);
        JsonNode started = data("POST", "/api/readings/tasks", Map.of(), fixture.token());
        Account account = accounts.findByUsername(fixture.username()).orElseThrow();
        account.setAreaScope("无设备的新辖区-" + FIXTURE_ID.incrementAndGet());
        accounts.saveAndFlush(account);
        long afterScopeChange = countReadings(fixture.meterIds());
        JsonNode stopped = awaitTask(started.path("id").asText(), admin);
        assertThat(stopped.path("status").asText()).isEqualTo("失败待重试");
        assertThat(stopped.path("failed").asInt()).isPositive();
        assertThat(countReadings(fixture.meterIds())).isEqualTo(afterScopeChange);
        assertThat(data("GET", "/api/meters", null, fixture.token()).path("total").asInt()).isZero();
        exchange("POST", "/api/readings/tasks/" + started.path("id").asText() + "/retry", Map.of(), fixture.token(), 403);
    }

    @Test
    void userCannotSubmitReadingsForAnotherMeter() throws Exception {
        exchange("POST", "/api/readings", Map.of("meterId", "A-00218", "ts", "2026-10-10 10:00:00",
                "instantFlow", 1, "cumulativeFlow", 2000, "temperature", 22, "valve", "开启", "alarmCode", "0"), resident, 403);
    }

    @Test
    void billingPaymentIsPersistentIdempotentAndServerCalculated() throws Exception {
        BoundFixture fixture = boundFixture("滨江花园");
        String period = YearMonth.now().toString();
        data("POST", "/api/bills/recalculate", Map.of("period", period,
                "dueDate", LocalDate.now().minusDays(3).toString()), finance);
        JsonNode bill = findBy(data("GET", "/api/bills/items?period=" + period, null, fixture.token()), "meterId", fixture.meterId());
        long id = bill.path("id").asLong();
        assertThat(id).isPositive();
        assertThat(bill.path("totalDue").decimalValue()).isPositive();
        exchange("POST", "/api/bills/items/" + id + "/pay", Map.of("idempotencyKey", "stolen-payment"), resident, 403);
        Map<String, Object> paymentBody = Map.of("idempotencyKey", "test-payment-" + id, "amount", 0.01);
        JsonNode payment = data("POST", "/api/bills/items/" + id + "/pay", paymentBody, fixture.token());
        JsonNode repeated = data("POST", "/api/bills/items/" + id + "/pay", paymentBody, fixture.token());
        assertThat(payment.path("id").asText()).isEqualTo(repeated.path("id").asText());
        JsonNode paid = findBy(data("GET", "/api/bills/items?period=" + period, null, fixture.token()), "meterId", fixture.meterId());
        assertThat(paid.path("status").asText()).isEqualTo("已缴");
        assertThat(paid.path("paidAmount").decimalValue()).isEqualByComparingTo(paid.path("totalDue").decimalValue());
        assertThat(data("GET", "/api/bills/payments", null, fixture.token())).hasSize(1);
        data("POST", "/api/bills/recalculate", Map.of("period", period), admin);
        JsonNode preserved = findBy(data("GET", "/api/bills/items?period=" + period, null, fixture.token()), "meterId", fixture.meterId());
        assertThat(preserved.path("status").asText()).isEqualTo("已缴");
        assertThat(preserved.path("paidAmount").decimalValue()).isEqualByComparingTo(paid.path("paidAmount").decimalValue());
    }

    @Test
    void aiAnswersUseScopedDatabaseNotGlobalFixtures() throws Exception {
        JsonNode fresh = registerFixture();
        JsonNode answer = data("POST", "/api/ai/query", Map.of("question", "我有多少水表和账单"), fresh.path("token").asText());
        assertThat(answer.path("answer").asText()).isNotBlank();
        assertThat(answer.path("answer").asText()).doesNotContain("青禾餐饮", "工业企业", "滨江花园商圈");
    }

    private String login(String username) throws Exception {
        return data("POST", "/api/auth/login", Map.of("username", username, "password", "admin123"), null).path("token").asText();
    }

    private JsonNode registerFixture() throws Exception {
        String name = "fixture_" + FIXTURE_ID.incrementAndGet();
        return data("POST", "/api/auth/register", Map.of("username", name, "password", "secure1234", "name", "测试住户"), null);
    }

    private BoundFixture boundFixture(String area) throws Exception {
        JsonNode account = registerFixture();
        String meterId = "A-" + FIXTURE_ID.incrementAndGet();
        meters.saveAndFlush(Meter.builder().meterId(meterId).userName("测试住户").area(area)
                .type(DomainEnums.MeterType.居民).status(DomainEnums.MeterStatus.在线)
                .lastReading(new BigDecimal("1000")).reading(new BigDecimal("1018.6"))
                .installTime(LocalDateTime.now().minusYears(1)).updatedAt(LocalDateTime.now()).build());
        data("PATCH", "/api/accounts/" + account.path("id").asLong() + "/binding", Map.of("boundMeterId", meterId), admin);
        return new BoundFixture(account.path("id").asLong(), account.path("username").asText(), account.path("token").asText(), meterId);
    }

    private Map<String, Object> meterBody(String meterId, String area) {
        return Map.of("meterId", meterId, "userName", "测试用户", "area", area, "type", "居民",
                "reading", 100, "lastReading", 90, "status", "在线");
    }

    private JsonNode awaitTask(String id, String token) throws Exception {
        long deadline = System.nanoTime() + 25_000_000_000L;
        while (System.nanoTime() < deadline) {
            JsonNode task = findBy(data("GET", "/api/readings/tasks", null, token), "id", id);
            if (List.of("已完成", "失败", "部分失败", "失败待重试").contains(task.path("status").asText())) return task;
            Thread.sleep(200);
        }
        throw new AssertionError("Reading task did not finish in 25 seconds");
    }

    private void stopAndDrainReadingTasks() throws Exception {
        data("PATCH", "/api/readings/scheduler", Map.of("enabled", false), admin);
        for (JsonNode task : data("GET", "/api/readings/tasks", null, admin)) {
            if (List.of("执行中", "补抄中").contains(task.path("status").asText()))
                awaitTask(task.path("id").asText(), admin);
        }
    }

    private OperatorFixture operatorFixture(String area, int count) throws Exception {
        JsonNode registered = registerFixture();
        String username = registered.path("username").asText();
        Account account = accounts.findByUsername(username).orElseThrow();
        account.setRole(DomainEnums.AccountRole.OPERATOR);
        account.setAreaScope(area);
        accounts.saveAndFlush(account);
        List<String> meterIds = new ArrayList<>();
        for (int n = 0; n < count; n++) {
            String meterId = "A-" + FIXTURE_ID.incrementAndGet();
            meters.saveAndFlush(Meter.builder().meterId(meterId).userName("权限撤销专用设备").area(area)
                    .type(DomainEnums.MeterType.居民).status(DomainEnums.MeterStatus.在线)
                    .lastReading(new BigDecimal("1000")).reading(new BigDecimal("1018.6"))
                    .installTime(LocalDateTime.now().minusYears(1)).updatedAt(LocalDateTime.now()).build());
            meterIds.add(meterId);
        }
        return new OperatorFixture(username, registered.path("token").asText(), meterIds);
    }

    private long countReadings(List<String> meterIds) {
        return readings.findAll().stream().filter(reading -> meterIds.contains(reading.getMeterId())).count();
    }

    private JsonNode data(String method, String path, Object body, String token) throws Exception {
        return exchange(method, path, body, token, 200).path("data");
    }

    private JsonNode exchange(String method, String path, Object body, String token, int expected) throws Exception {
        MvcResult result = raw(method, path, body, token);
        String payload = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode json = mapper.readTree(payload);
        assertThat(result.getResponse().getStatus()).as("%s %s (%s: %s)", method, path,
                json.path("code").asText(), json.path("message").asText()).isEqualTo(expected);
        return json;
    }

    private MvcResult raw(String method, String path, Object body, String token) throws Exception {
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON);
        if (body != null) builder.content(mapper.writeValueAsBytes(body));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return mvc.perform(builder).andReturn();
    }

    private JsonNode findBy(JsonNode array, String key, String value) {
        return matching(array, key, value).stream().findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + key + "=" + value));
    }

    private List<JsonNode> matching(JsonNode array, String key, String value) {
        List<JsonNode> rows = new ArrayList<>();
        for (JsonNode row : array) if (row.path(key).asText().equals(value)) rows.add(row);
        return rows;
    }

    private record BoundFixture(long accountId, String username, String token, String meterId) {}
    private record OperatorFixture(String username, String token, List<String> meterIds) {}
}
