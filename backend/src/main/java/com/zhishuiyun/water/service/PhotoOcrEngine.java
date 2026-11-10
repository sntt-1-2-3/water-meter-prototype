package com.zhishuiyun.water.service;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import net.sourceforge.tess4j.util.LoadLibs;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Real local Tesseract OCR. A proposal is never an authoritative billing reading. */
@Component
public class PhotoOcrEngine {
    private static final String ENGINE = "Tesseract 5 / Tess4J 5.19.0 + roller segmentation (local)";
    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\d.,])\\d{1,12}(?:[.,]\\d{1,3})?(?![\\p{L}\\d.,])");
    private final Semaphore capacity = new Semaphore(1);
    private volatile Path dataPath;

    public record Region(int x, int y, int width, int height) {}
    public record OcrResult(String rawText, List<String> candidates, Double confidence, String engine, String error,
                            Region region) {
        public OcrResult(String rawText, List<String> candidates, Double confidence, String engine, String error) {
            this(rawText, candidates, confidence, engine, error, null);
        }
    }

    public OcrResult recognize(BufferedImage image) {
        if (image == null || image.getWidth() < 1 || image.getHeight() < 1
                || (long) image.getWidth() * image.getHeight() > 12_000_000L)
            return failure("图片尺寸无效或超过1200万像素", "");
        if (!capacity.tryAcquire()) return failure("本地识别引擎正在处理其他照片，请稍后重新识别或人工填写", "");
        try {
            Tesseract engine = new Tesseract();
            engine.setDatapath(tessdata().toString());
            engine.setLanguage("eng");
            engine.setOcrEngineMode(1);
            engine.setVariable("user_defined_dpi", "300");
            engine.setVariable("load_system_dawg", "0");
            engine.setVariable("load_freq_dawg", "0");
            MeterDigitLocator.Window window = MeterDigitLocator.locate(image);
            if (window != null) return readWindow(engine, image, window);
            boolean line = image.getWidth() >= image.getHeight() * 2L;
            // Restrict digits only for a user-cropped line; whole faces also contain model/serial numbers.
            if (line) engine.setVariable("tessedit_char_whitelist", "0123456789.,");
            List<Scored> proposals = new ArrayList<>();
            StringBuilder raw = new StringBuilder();
            // At most three bounded-size passes. No external service, URLs, or image-path execution.
            for (int pass = 0; pass < (line ? 3 : 2); pass++) {
                BufferedImage prepared = prepare(image, pass, line);
                engine.setPageSegMode(line ? (pass == 0 ? 7 : pass == 1 ? 13 : 8) : 11);
                List<Word> words = engine.getWords(prepared, ITessAPI.TessPageIteratorLevel.RIL_WORD);
                raw.append(pass == 0 ? "[原图识别]\n" : pass == 1 ? "\n[灰度增强识别]\n" : "\n[二值增强识别]\n");
                for (Word word : words) {
                    String text = word.getText() == null ? "" : word.getText().trim();
                    if (text.isEmpty()) continue;
                    raw.append(text).append(' ');
                    int digits = (int) text.chars().filter(Character::isDigit).count();
                    for (String number : extractCandidates(text)) proposals.add(new Scored(number,
                            Float.isFinite(word.getConfidence()) ? (double) word.getConfidence() : 0, digits));
                }
                raw.append('\n');
            }
            LinkedHashMap<String, Double> unique = new LinkedHashMap<>();
            Comparator<Scored> ordering = line ? Comparator.comparingInt(Scored::digits).reversed()
                    .thenComparing(Comparator.comparingDouble(Scored::score).reversed())
                    : Comparator.comparingDouble(Scored::score).reversed();
            proposals.stream().sorted(ordering)
                    .forEach(proposal -> unique.merge(proposal.value(), proposal.score(), Math::max));
            // Unlocated face text may be a model, serial, temperature or scale, never a proposed meter reading.
            List<String> values = line ? unique.keySet().stream().limit(10).toList() : List.of();
            Double confidence = values.isEmpty() ? null : Math.max(0, Math.min(100, unique.get(values.get(0))));
            String warning = !line ? "未能可靠定位数字窗口；整图文字可能包含型号或表号，请框选读数窗口后识别"
                    : values.isEmpty() ? "未识别出清晰数字；请框选数字窗口、旋转或人工填写"
                    : values.size() > 1 ? "识别到多个数字，可能包含型号或表号；请框选读数窗口并核对小数位" : null;
            return new OcrResult(raw.substring(0, Math.min(16000, raw.length())), values, confidence, ENGINE, warning);
        } catch (Exception | LinkageError error) {
            // Do not expose filesystem paths/native exception internals or fabricate a successful reading.
            return failure("本地 OCR 引擎暂不可用，请检查原生运行库，或根据原图人工填写后确认", "");
        } catch (Error error) {
            // JNA can report a native access violation as plain Error; do not swallow JVM resource failures.
            if (error.getClass() != Error.class || !"Invalid memory access".equals(error.getMessage())) throw error;
            return failure("本地 OCR 原生库不兼容，请检查运行库版本，或根据原图人工填写后确认", "");
        } finally { capacity.release(); }
    }

    private OcrResult readWindow(Tesseract engine, BufferedImage image, MeterDigitLocator.Window window) {
        engine.setVariable("tessedit_char_whitelist", "0123456789");
        StringBuilder digits = new StringBuilder();
        StringBuilder trace = new StringBuilder("[自动定位数字窗口]\n检测到 ")
                .append(window.digits().size()).append(" 位滚轮，其中末尾 ")
                .append(window.decimalPlaces()).append(" 位为红字。\n[逐位识别]\n");
        double confidence = 0;
        int scores = 0;
        boolean complete = true;
        for (MeterDigitLocator.Digit digit : window.digits()) {
            if (Thread.currentThread().isInterrupted()) return failure("识别等待已结束，请重新框选或人工填写", trace.toString());
            BufferedImage prepared = MeterDigitLocator.digitImage(image, digit, 120);
            String agreed = null;
            boolean consistent = true;
            for (int mode : new int[]{8, 13}) {
                engine.setPageSegMode(mode);
                List<Word> words = engine.getWords(prepared, ITessAPI.TessPageIteratorLevel.RIL_WORD);
                String text = words.stream().map(Word::getText).filter(java.util.Objects::nonNull)
                        .reduce("", String::concat).replaceAll("\\s+", "");
                if (!text.matches("[0-9]") || agreed != null && !agreed.equals(text)) consistent = false;
                if (agreed == null) agreed = text;
                if (words.size() == 1 && Float.isFinite(words.get(0).getConfidence())) {
                    confidence += Math.max(0, Math.min(100, words.get(0).getConfidence())); scores++;
                }
            }
            if (!consistent || agreed == null || agreed.isEmpty()) { complete = false; digits.append('?'); }
            else digits.append(agreed);
        }
        trace.append(digits).append('\n');
        var box = window.bounds();
        Region region = new Region(box.x, box.y, box.width, box.height);
        if (!complete) return new OcrResult(trace.toString(), List.of(), null, ENGINE,
                "已定位数字窗口，但部分数字不清晰或识别结果不一致；请放大核对、重新框选或人工填写", region);
        int split = digits.length() - window.decimalPlaces();
        String proposed = digits.substring(0, split) + "." + digits.substring(split);
        List<String> values = extractCandidates(proposed);
        trace.append("[小数位建议]\n按检测到的黑字/红字分界建议 ").append(proposed)
                .append(" m³。请人工核对红字位数、滚轮半转数字和表盘单位后确认。\n");
        return new OcrResult(trace.toString(), values, scores == 0 ? null : confidence / scores, ENGINE,
                values.isEmpty() ? "识别数值超过系统范围，请人工核对" : null, region);
    }

    private Path tessdata() throws Exception {
        if (dataPath != null) return dataPath;
        String override = System.getProperty("photo.ocr.tessdata", "");
        Path located = override.isBlank() ? LoadLibs.extractTessResources("tessdata").toPath() : Path.of(override);
        if (!Files.isRegularFile(located.resolve("eng.traineddata"))) throw new IllegalStateException("Missing English OCR model");
        dataPath = located;
        return located;
    }

    static List<String> extractCandidates(String text) {
        List<String> values = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group().replace(',', '.');
            // Leading zeros may be removed, but a missing decimal point is NEVER inferred.
            BigDecimal number = new BigDecimal(raw);
            if (number.compareTo(new BigDecimal("999999999.999")) > 0) continue;
            String normalized = number.stripTrailingZeros().toPlainString();
            if (normalized.matches("[0-9]{1,9}(\\.[0-9]{1,3})?")) values.add(normalized);
        }
        return values.stream().distinct().toList();
    }

    private BufferedImage prepare(BufferedImage input, int mode, boolean line) {
        double scale = Math.min(line ? 3.0 : 1.0, 1800.0 / Math.max(input.getWidth(), input.getHeight()));
        int width = Math.max(1, (int) Math.round(input.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(input.getHeight() * scale));
        int padding = line ? 24 : 8;
        BufferedImage output = new BufferedImage(width + padding * 2, height + padding * 2,
                mode > 0 ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, output.getWidth(), output.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(input, padding, padding, width, height, null);
        } finally { graphics.dispose(); }
        if (mode > 0) {
            // Minimum RGB channel retains red fractional digits, which luminance gray can wash out.
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D scaleGraphics = scaled.createGraphics();
            try {
                scaleGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                scaleGraphics.drawImage(input, 0, 0, width, height, null);
            } finally { scaleGraphics.dispose(); }
            int[] histogram = new int[256];
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int pixel = scaled.getRGB(x, y);
                int channel = Math.min((pixel >>> 16) & 255, Math.min((pixel >>> 8) & 255, pixel & 255));
                output.getRaster().setSample(x + padding, y + padding, 0, channel); histogram[channel]++;
            }
            if (mode == 2) {
                int threshold = otsu(histogram, width * height);
                for (int y = padding; y < height + padding; y++) for (int x = padding; x < width + padding; x++)
                    output.getRaster().setSample(x, y, 0, output.getRaster().getSample(x, y, 0) <= threshold ? 0 : 255);
            }
        }
        return output;
    }

    private int otsu(int[] histogram, int total) {
        long sum = 0; for (int n = 0; n < 256; n++) sum += (long) n * histogram[n];
        long backgroundSum = 0; int backgroundCount = 0; int threshold = 128; double best = -1;
        for (int n = 0; n < 256; n++) {
            backgroundCount += histogram[n]; if (backgroundCount == 0) continue;
            int foregroundCount = total - backgroundCount; if (foregroundCount == 0) break;
            backgroundSum += (long) n * histogram[n];
            double meanDifference = backgroundSum / (double) backgroundCount - (sum - backgroundSum) / (double) foregroundCount;
            double variance = (double) backgroundCount * foregroundCount * meanDifference * meanDifference;
            if (variance > best) { best = variance; threshold = n; }
        }
        return threshold;
    }

    private OcrResult failure(String message, String raw) { return new OcrResult(raw, List.of(), null, ENGINE, message); }
    private record Scored(String value, double score, int digits) {}
}
