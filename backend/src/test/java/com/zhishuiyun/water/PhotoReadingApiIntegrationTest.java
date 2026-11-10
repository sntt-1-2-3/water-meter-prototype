package com.zhishuiyun.water;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishuiyun.water.entity.DomainEnums;
import com.zhishuiyun.water.entity.Meter;
import com.zhishuiyun.water.entity.Reading;
import com.zhishuiyun.water.repository.MeterRepository;
import com.zhishuiyun.water.repository.ReadingRepository;
import com.zhishuiyun.water.service.PhotoOcrEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/** Tests workflow/security with deterministic OCR; PhotoOcrEngineTest separately exercises the real engine. */
@SpringBootTest(properties = {
        "simulator.enabled=false", "deepseek.enabled=false", "spring.jpa.hibernate.ddl-auto=create",
        "jwt.secret=photo-test-only-signing-secret-not-for-deployment-01234567890123456789"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PhotoReadingApiIntegrationTest {
    private static final AtomicInteger SEQUENCE = new AtomicInteger(70000);
    private static final String RUN_ID = UUID.randomUUID().toString();
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        Path directory = Path.of("target", "photo-integration-tests").toAbsolutePath();
        Files.createDirectories(directory);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + directory.resolve(RUN_ID + ".db"));
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired MeterRepository meters;
    @Autowired ReadingRepository readings;
    @MockBean PhotoOcrEngine ocr;
    private String admin;
    private String operator;
    private String resident;
    private String finance;
    private String auditor;
    private byte[] image;

    @BeforeAll
    void setup() throws Exception {
        admin = login("admin"); operator = login("operator_east"); resident = login("user_res_001");
        finance = login("finance01"); auditor = login("auditor01");
        BufferedImage picture = new BufferedImage(600, 160, BufferedImage.TYPE_INT_RGB);
        Graphics2D drawing = picture.createGraphics();
        drawing.setColor(Color.WHITE); drawing.fillRect(0, 0, 600, 160);
        drawing.setColor(Color.BLACK); drawing.setFont(new Font("Monospaced", Font.BOLD, 70));
        drawing.drawString("00070.812", 20, 110); drawing.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ImageIO.write(picture, "png", bytes); image = bytes.toByteArray();
    }

    @BeforeEach
    void deterministicSuggestion() {
        when(ocr.recognize(any(BufferedImage.class))).thenReturn(new PhotoOcrEngine.OcrResult(
                "00070.812", List.of("70.812"), 86.0, "TEST_OCR", null));
    }

    @Test
    void deferredUploadPersistsOriginalBeforeOcrAndCanRecognizeLater() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        when(ocr.recognize(any(BufferedImage.class))).thenAnswer(call -> {
            calls.incrementAndGet();
            return new PhotoOcrEngine.OcrResult("70.812", List.of("70.812"), 86.0, "TEST_OCR", null);
        });
        Meter meter = fixture("滨江花园");
        JsonNode draft = upload(meter.getMeterId(), admin, image, 200, false).path("data");
        assertThat(calls.get()).isZero();
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.path("recognizedAt").isNull()).isTrue();
        assertThat(draft.path("suggestedReading").isNull()).isTrue();
        assertThat(draft.path("candidates").isEmpty()).isTrue();
        var original = mvc.perform(get(path(draft) + "/image").header("Authorization", "Bearer " + admin)).andReturn();
        assertThat(original.getResponse().getStatus()).isEqualTo(200);
        JsonNode recognized = json("POST", path(draft) + "/recognize", Map.of("rotation", 0), admin, 200).path("data");
        assertThat(recognized.path("suggestedReading").asText()).isEqualTo("70.812");
        assertThat(recognized.path("recognizedAt").isTextual()).isTrue();
        assertThat(calls.get()).isEqualTo(1);
        assertThat(count(meter)).isZero();
    }

    @Test
    void savedPhotoCanBeManuallyConfirmedWithoutWaitingForOcr() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        when(ocr.recognize(any(BufferedImage.class))).thenAnswer(call -> {
            calls.incrementAndGet();
            throw new IllegalStateException("Must not run for deferred upload or manual confirmation");
        });
        Meter meter = fixture("滨江花园");
        JsonNode draft = upload(meter.getMeterId(), admin, image, 200, false).path("data");
        JsonNode saved = json("POST", path(draft) + "/confirm", confirmation("71.5", "60"), admin, 200).path("data");
        assertThat(saved.path("confirmedReading").asText()).isEqualTo("71.5");
        assertThat(saved.path("manuallyEdited").asBoolean()).isTrue();
        assertThat(calls.get()).isZero();
        assertThat(count(meter)).isEqualTo(1);
    }

    @Test
    void uploadCreatesOnlyDraftAndAuthenticatedNormalizedPhoto() throws Exception {
        Meter meter = fixture("滨江花园");
        JsonNode draft = upload(meter.getMeterId(), admin, image, 200).path("data");
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.path("candidates").get(0).asText()).isEqualTo("70.812");
        assertThat(new BigDecimal(draft.path("previousReading").asText())).isEqualByComparingTo("60.000");
        assertThat(count(meter)).isZero();
        assertThat(meters.findByMeterId(meter.getMeterId()).orElseThrow().getReading()).isEqualByComparingTo("60");
        MvcResult photo = mvc.perform(get(path(draft) + "/image").header("Authorization", "Bearer " + admin)).andReturn();
        assertThat(photo.getResponse().getStatus()).isEqualTo(200);
        assertThat(photo.getResponse().getContentType()).startsWith("image/png");
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(photo.getResponse().getContentAsByteArray()))).isNotNull();
        json("GET", path(draft) + "/image", null, null, 401);
    }

    @Test
    void requiresReadingRoleAndChecksAreaForUploadDetailAndPhoto() throws Exception {
        Meter outside = fixture("照片测试西区");
        upload(outside.getMeterId(), null, image, 401);
        for (String token : List.of(resident, finance, auditor, operator)) upload(outside.getMeterId(), token, image, 403);
        JsonNode draft = upload(outside.getMeterId(), admin, image, 200).path("data");
        for (String token : List.of(resident, finance, auditor, operator)) {
            json("GET", path(draft), null, token, 403);
            json("GET", path(draft) + "/image", null, token, 403);
            json("POST", path(draft) + "/confirm", confirmation("70.812", "60.000"), token, 403);
        }
        JsonNode visible = json("GET", "/api/readings/photos?pageSize=50", null, operator, 200).path("data").path("items");
        for (JsonNode row : visible) assertThat(row.path("meterId").asText()).isNotEqualTo(outside.getMeterId());
    }

    @Test
    void operatorCanConfirmWithinAreaAndHumanCorrectionIsAudited() throws Exception {
        Meter meter = fixture("滨江花园");
        JsonNode draft = upload(meter.getMeterId(), operator, image, 200).path("data");
        JsonNode saved = json("POST", path(draft) + "/confirm", Map.of(
                "reading", "71.125", "expectedPreviousReading", "60.000", "note", "已核对红字小数位，人工修正"), operator, 200).path("data");
        assertThat(saved.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(saved.path("confirmedReading").asText()).isEqualTo("71.125");
        assertThat(new BigDecimal(saved.path("confirmedPreviousReading").asText())).isEqualByComparingTo("60.000");
        assertThat(saved.path("confirmedBy").asText()).isEqualTo("operator_east");
        assertThat(saved.path("manuallyEdited").asBoolean()).isTrue();
        Reading reading = readings.findFirstByMeterIdOrderByTsDesc(meter.getMeterId()).orElseThrow();
        assertThat(reading.getCumulativeFlow()).isEqualByComparingTo("71.125");
        assertThat(reading.getInstantFlow()).isNull(); assertThat(reading.getTemperature()).isNull();
        assertThat(reading.getSource()).isEqualTo("PHOTO");
        assertThat(reading.getPhotoReadingId()).isEqualTo(draft.path("id").asLong());
        assertThat(count(meter)).isEqualTo(1);
    }

    @Test
    void failedOcrRemainsDraftAndCanBeExplicitlyManuallyEntered() throws Exception {
        when(ocr.recognize(any(BufferedImage.class))).thenReturn(new PhotoOcrEngine.OcrResult(
                "", List.of(), null, "TESSERACT", "未识别出清晰数字，请框选读数或人工填写"));
        Meter meter = fixture("滨江花园");
        JsonNode draft = upload(meter.getMeterId(), admin, image, 200).path("data");
        assertThat(draft.path("candidates").isEmpty()).isTrue(); assertThat(draft.path("ocrError").asText()).isNotBlank();
        assertThat(count(meter)).isZero();
        JsonNode saved = json("POST", path(draft) + "/confirm", confirmation("65.500", "60"), admin, 200).path("data");
        assertThat(saved.path("manuallyEdited").asBoolean()).isTrue(); assertThat(count(meter)).isEqualTo(1);
    }

    @Test
    void rejectsInvalidImagesAndOversizedFilesWithoutSavingReadings() throws Exception {
        Meter meter = fixture("滨江花园");
        upload(meter.getMeterId(), admin, "<svg onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8), 400);
        upload(meter.getMeterId(), admin, new byte[0], 400);
        upload(meter.getMeterId(), admin, new byte[8 * 1024 * 1024 + 1], 413);
        assertThat(count(meter)).isZero();
    }

    @Test
    void validatesDecimalRangeAndDoesNotAcceptRollback() throws Exception {
        Meter meter = fixture("滨江花园");
        JsonNode draft = upload(meter.getMeterId(), admin, image, 200).path("data");
        for (String value : List.of("59.999", "-1", "70.1234", "1000000000", "1e5", "NaN", "", ".5"))
            json("POST", path(draft) + "/confirm", confirmation(value, "60.000"), admin, 400);
        assertThat(count(meter)).isZero();
    }

    @Test
    void staleBaselineAndChangedMeterAreRejectedWithoutOverwritingNewReadings() throws Exception {
        Meter meter = fixture("滨江花园");
        JsonNode draft = upload(meter.getMeterId(), admin, image, 200).path("data");
        meter.setReading(new BigDecimal("62")); meters.saveAndFlush(meter);
        json("POST", path(draft) + "/confirm", confirmation("70.812", "60.000"), admin, 409);
        assertThat(count(meter)).isZero();
        assertThat(meters.findByMeterId(meter.getMeterId()).orElseThrow().getReading()).isEqualByComparingTo("62");
    }

    @Test
    void sameConfirmationIsIdempotentButDifferentValueCannotRewriteSavedDraft() throws Exception {
        Meter meter = fixture("滨江花园");
        JsonNode draft = upload(meter.getMeterId(), admin, image, 200).path("data");
        JsonNode first = json("POST", path(draft) + "/confirm", confirmation("70.812", "60"), admin, 200).path("data");
        JsonNode again = json("POST", path(draft) + "/confirm", confirmation("70.812", "60"), admin, 200).path("data");
        assertThat(again.path("readingId").asLong()).isEqualTo(first.path("readingId").asLong());
        json("POST", path(draft) + "/confirm", confirmation("72", "60"), admin, 409);
        json("POST", path(draft) + "/recognize", Map.of("rotation", 0), admin, 409);
        assertThat(count(meter)).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateConfirmationWritesOnlyOneReading() throws Exception {
        Meter meter = fixture("滨江花园");
        JsonNode draft = upload(meter.getMeterId(), admin, image, 200).path("data");
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> json("POST", path(draft) + "/confirm", confirmation("70.812", "60"), admin, 200));
            var second = pool.submit(() -> json("POST", path(draft) + "/confirm", confirmation("70.812", "60"), admin, 200));
            assertThat(first.get().path("data").path("readingId").asLong())
                    .isEqualTo(second.get().path("data").path("readingId").asLong());
        } finally { pool.shutdownNow(); }
        assertThat(count(meter)).isEqualTo(1);
    }

    @Test
    void cropAndRotationAreValidatedAndDoNotWriteBusinessReading() throws Exception {
        Meter meter = fixture("滨江花园");
        JsonNode draft = upload(meter.getMeterId(), admin, image, 200).path("data");
        json("POST", path(draft) + "/recognize", Map.of("rotation", 45), admin, 400);
        json("POST", path(draft) + "/recognize", Map.of("rotation", 0, "crop", Map.of("x", -1, "y", 0, "width", 50, "height", 50)), admin, 400);
        json("POST", path(draft) + "/recognize", Map.of("rotation", 90, "crop", Map.of("x", 10, "y", 20, "width", 100, "height", 500)), admin, 200);
        JsonNode visible = json("GET", path(draft), null, admin, 200).path("data");
        assertThat(visible.path("rotation").asInt()).isEqualTo(90);
        assertThat(visible.path("crop").path("width").asInt()).isEqualTo(100);
        assertThat(count(meter)).isZero();
    }

    @Test
    void automaticWindowIsMappedBackToRotatedOriginalAndSurvivesReload() throws Exception {
        Meter meter = fixture("滨江花园");
        JsonNode draft = upload(meter.getMeterId(), admin, image, 200, false).path("data");
        when(ocr.recognize(any(BufferedImage.class))).thenReturn(new PhotoOcrEngine.OcrResult(
                "[自动定位数字窗口]\n00070812", List.of("70.812"), 80.0, "TEST_OCR", null,
                new PhotoOcrEngine.Region(12, 30, 50, 90)));
        json("POST", path(draft) + "/recognize", Map.of("rotation", 90, "crop", Map.of(
                "x", 10, "y", 20, "width", 100, "height", 500)), admin, 200);
        JsonNode reloaded = json("GET", path(draft), null, admin, 200).path("data");
        assertThat(reloaded.path("rotation").asInt()).isEqualTo(90);
        assertThat(reloaded.path("crop").path("x").asInt()).isEqualTo(22);
        assertThat(reloaded.path("crop").path("y").asInt()).isEqualTo(50);
        assertThat(reloaded.path("crop").path("width").asInt()).isEqualTo(50);
        assertThat(reloaded.path("crop").path("height").asInt()).isEqualTo(90);
        assertThat(reloaded.path("suggestedReading").asText()).isEqualTo("70.812");
        assertThat(reloaded.path("imageWidth").asInt()).isEqualTo(600);
        assertThat(count(meter)).isZero();
    }

    @Test
    void confirmedPhotoIsUsedForBillingAndExistingSnapshotIsNotOverwritten() throws Exception {
        Meter meter = fixture("滨江花园");
        JsonNode draft = upload(meter.getMeterId(), admin, image, 200).path("data");
        json("POST", path(draft) + "/confirm", confirmation("70.812", "60.000"), admin, 200);
        String period = YearMonth.now().toString();
        json("POST", "/api/bills/recalculate", Map.of("period", period), finance, 200);
        JsonNode items = json("GET", "/api/bills/items?period=" + period, null, finance, 200).path("data");
        JsonNode bill = null;
        for (JsonNode row : items) if (meter.getMeterId().equals(row.path("meterId").asText())) bill = row;
        assertThat(bill).isNotNull();
        assertThat(bill.path("closingReading").decimalValue()).isEqualByComparingTo("70.812");
        assertThat(bill.path("usage").decimalValue()).isEqualByComparingTo("10.812");
        JsonNode newer = upload(meter.getMeterId(), admin, image, 200).path("data");
        json("POST", path(newer) + "/confirm", confirmation("75", "70.812"), admin, 409);
        assertThat(count(meter)).isEqualTo(1);
    }

    private Meter fixture(String area) {
        return meters.saveAndFlush(Meter.builder().meterId("A-" + SEQUENCE.incrementAndGet()).userName("照片抄表测试")
                .area(area).type(DomainEnums.MeterType.居民).status(DomainEnums.MeterStatus.在线)
                .lastReading(new BigDecimal("60.000")).reading(new BigDecimal("60.000"))
                .installTime(LocalDateTime.now().minusYears(1)).updatedAt(LocalDateTime.now().minusDays(1)).build());
    }
    private long count(Meter meter) { return readings.findAll().stream().filter(r -> meter.getMeterId().equals(r.getMeterId())).count(); }
    private Map<String, String> confirmation(String reading, String previous) { return Map.of("reading", reading, "expectedPreviousReading", previous); }
    private String path(JsonNode draft) { return "/api/readings/photos/" + draft.path("id").asText(); }
    private String login(String username) throws Exception { return json("POST", "/api/auth/login", Map.of("username", username, "password", "admin123"), null, 200).path("data").path("token").asText(); }
    private JsonNode upload(String meter, String token, byte[] bytes, int expected) throws Exception {
        return upload(meter, token, bytes, expected, true);
    }
    private JsonNode upload(String meter, String token, byte[] bytes, int expected, boolean recognize) throws Exception {
        var builder = multipart("/api/readings/photos").file(new MockMultipartFile("file", "untrusted-name.png", "image/png", bytes)).param("meterId", meter).param("recognize", Boolean.toString(recognize));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        MvcResult result = mvc.perform(builder).andReturn();
        JsonNode payload = mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(result.getResponse().getStatus()).as("photo upload: %s", payload.path("message").asText()).isEqualTo(expected);
        return payload;
    }
    private JsonNode json(String method, String path, Object body, String token, int expected) throws Exception {
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON);
        if (body != null) builder.content(mapper.writeValueAsBytes(body));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        MvcResult result = mvc.perform(builder).andReturn();
        JsonNode payload = mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(result.getResponse().getStatus()).as("%s %s: %s", method, path, payload.path("message").asText()).isEqualTo(expected);
        return payload;
    }
}
