package com.zhishuiyun.water.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishuiyun.water.common.*;
import com.zhishuiyun.water.dto.*;
import com.zhishuiyun.water.entity.*;
import com.zhishuiyun.water.repository.*;
import com.zhishuiyun.water.security.AuthUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Private local-photo workflow: an OCR draft never mutates meter or device readings. */
@Service
public class PhotoReadingService {
    private static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    private static final long MAX_PIXELS = 12_000_000L;
    private static final Pattern DECIMAL = Pattern.compile("^[0-9]{1,9}(\\.[0-9]{1,3})?$");
    // Enter this lock BEFORE opening the transaction; release only AFTER its commit.
    // The existing SQLite datasource has a single connection, serializing other writers too.
    private static final Object CONFIRM_LOCK = new Object();

    private final PhotoReadingRepository photos;
    private final MeterRepository meters;
    private final ReadingRepository readings;
    private final ReadingTaskRepository tasks;
    private final BillItemRepository billItems;
    private final AccessPolicy access;
    private final AuditService audit;
    private final PhotoOcrEngine ocr;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;

    public PhotoReadingService(PhotoReadingRepository photos, MeterRepository meters,
                               ReadingRepository readings, ReadingTaskRepository tasks,
                               BillItemRepository billItems, AccessPolicy access, AuditService audit,
                               PhotoOcrEngine ocr, ObjectMapper mapper, PlatformTransactionManager transactions) {
        this.photos = photos; this.meters = meters; this.readings = readings; this.tasks = tasks;
        this.billItems = billItems; this.access = access; this.audit = audit;
        this.ocr = ocr; this.mapper = mapper; this.transaction = new TransactionTemplate(transactions);
    }

    public PhotoReadingDto upload(String meterId, MultipartFile file, AuthUser user) {
        access.requireReadings(user);
        inTransaction(() -> authorizedMeter(meterId, user));
        byte[] supplied = readUpload(file);
        BufferedImage image = decode(supplied);
        byte[] normalized = encodePng(image);
        // Native OCR and image decoding run with NO database connection/transaction held.
        Recognition result = recognizeImage(image);
        return inTransaction(() -> {
            Meter meter = authorizedMeter(meterId, user);
            PhotoReading photo = PhotoReading.builder().meterId(meterId).status("DRAFT")
                    .createdAt(LocalDateTime.now()).uploadedBy(user.getUsername())
                    .imageBytes(normalized).imageWidth(image.getWidth()).imageHeight(image.getHeight())
                    .imageMime("image/png").rotation(0).manuallyEdited(false).build();
            applyRecognition(photo, result, 0, null);
            photos.saveAndFlush(photo);
            audit.log(user.getUsername(), user.getRole().name(), "PHOTO_READING_UPLOAD", String.valueOf(photo.getId()),
                    RequestContext.getRequestId(), "DRAFT", null, meterId + ";engine=" + result.engine());
            return dto(photo, meter);
        });
    }

    public PageResult<PhotoReadingDto> list(String meterId, int page, int pageSize, AuthUser user) {
        access.requireReadings(user);
        if (page < 1 || pageSize < 1 || pageSize > 50 || (long) (page - 1) * pageSize > Integer.MAX_VALUE)
            throw bad("页码须大于0，每页照片条数须为1至50");
        return inTransaction(() -> {
            if (meterId != null && !meterId.isBlank()) authorizedMeter(meterId, user);
            List<String> ids = meterId != null && !meterId.isBlank() ? List.of(meterId)
                    : access.visibleMeters(user).stream().map(Meter::getMeterId).toList();
            if (ids.isEmpty()) return PageResult.of(List.of(), 0, page, pageSize);
            var found = photos.findByMeterIdIn(ids, PageRequest.of(page - 1, pageSize, Sort.by("id").descending()));
            List<PhotoReadingDto> items = found.getContent().stream()
                    .map(photo -> dto(photo, authorizedMeter(photo.getMeterId(), user))).toList();
            return PageResult.of(items, found.getTotalElements(), page, pageSize);
        });
    }

    public PhotoReadingDto detail(Long id, AuthUser user) {
        access.requireReadings(user);
        return inTransaction(() -> {
            PhotoReading photo = requiredPhoto(id);
            return dto(photo, authorizedMeter(photo.getMeterId(), user));
        });
    }

    public byte[] image(Long id, AuthUser user) {
        access.requireReadings(user);
        return inTransaction(() -> {
            PhotoReading photo = requiredPhoto(id);
            authorizedMeter(photo.getMeterId(), user);
            return photo.getImageBytes().clone();
        });
    }

    public PhotoReadingDto recognize(Long id, PhotoRecognizeRequest request, AuthUser user) {
        access.requireReadings(user);
        int rotation = request == null || request.rotation() == null ? 0 : request.rotation();
        if (!Set.of(0, 90, 180, 270).contains(rotation)) throw bad("旋转角度只能为0、90、180或270");
        PhotoReadingDto.Crop crop = request == null ? null : request.crop();
        byte[] bytes = inTransaction(() -> {
            PhotoReading photo = requiredPhoto(id);
            authorizedMeter(photo.getMeterId(), user);
            requireDraft(photo);
            return photo.getImageBytes().clone();
        });
        BufferedImage transformed = rotate(decode(bytes), rotation);
        if (crop != null) transformed = crop(transformed, crop);
        Recognition result = recognizeImage(transformed);
        synchronized (CONFIRM_LOCK) {
            return inTransaction(() -> {
                PhotoReading photo = requiredPhoto(id);
                Meter meter = authorizedMeter(photo.getMeterId(), user);
                requireDraft(photo); // Confirmation could have happened while OCR ran.
                String previous = photo.getCandidatesJson();
                applyRecognition(photo, result, rotation, crop);
                photos.saveAndFlush(photo);
                audit.log(user.getUsername(), user.getRole().name(), "PHOTO_READING_RECOGNIZE", String.valueOf(id),
                        RequestContext.getRequestId(), "DRAFT", previous,
                        photo.getCandidatesJson() + ";rotation=" + rotation + ";crop=" + crop);
                return dto(photo, meter);
            });
        }
    }

    public PhotoReadingDto confirm(Long id, PhotoConfirmRequest request, AuthUser user) {
        access.requireReadings(user);
        if (request == null) throw bad("请提交经人工核对的读数和上次读数");
        BigDecimal confirmed = decimal(request.reading(), "确认读数");
        BigDecimal expected = decimal(request.expectedPreviousReading(), "上次读数");
        if (request.note() != null && request.note().length() > 2000) throw bad("人工备注不得超过2000字");
        synchronized (CONFIRM_LOCK) {
            return inTransaction(() -> {
                PhotoReading photo = requiredPhoto(id);
                Meter meter = authorizedMeter(photo.getMeterId(), user);
                if ("CONFIRMED".equals(photo.getStatus())) {
                    if (new BigDecimal(photo.getConfirmedReading()).compareTo(confirmed) == 0) return dto(photo, meter);
                    throw conflict("该照片已经按其他读数确认，不能修改已入库记录");
                }
                requireDraft(photo);
                if (hasActiveTask(meter.getMeterId()))
                    throw conflict("该水表有正在执行的模拟抄表任务，请等待任务完成后刷新并重新核对");
                if (billItems.findFirstByMeterIdAndPeriod(meter.getMeterId(), YearMonth.now().toString()).isPresent())
                    throw new BusinessException(ErrorCode.BILL_ALREADY_EXISTS,
                            "该水表本账期已有账单快照，不能再确认本账期照片读数；请使用新账期，已有或已缴账单不可重算");
                BigDecimal baseline = baseline(meter);
                if (baseline.compareTo(expected) != 0)
                    throw conflict("水表最新读数已变为 " + plain(baseline) + "，请刷新照片详情并重新人工核对");
                if (confirmed.compareTo(baseline) < 0) throw bad("确认读数不能小于当前最新读数 " + plain(baseline));
                LocalDateTime now = LocalDateTime.now();
                Reading latest = readings.findFirstByMeterIdOrderByTsDesc(meter.getMeterId()).orElse(null);
                LocalDateTime ts = latest != null && !now.isAfter(latest.getTs()) ? latest.getTs().plusNanos(1) : now;
                Reading reading = Reading.builder().meterId(meter.getMeterId()).ts(ts)
                        .cumulativeFlow(confirmed).source("PHOTO").photoReadingId(photo.getId())
                        // A picture supplies no measured flow, temperature, valve, or alarm telemetry.
                        .instantFlow(null).temperature(null).valve(null).alarmCode(null).build();
                readings.saveAndFlush(reading);
                meter.setReading(confirmed); meter.setUpdatedAt(ts);
                meters.saveAndFlush(meter);
                photo.setStatus("CONFIRMED"); photo.setConfirmedReading(plain(confirmed));
                photo.setConfirmedPreviousReading(plain(baseline)); photo.setConfirmedBy(user.getUsername());
                photo.setConfirmedAt(now); photo.setReadingId(reading.getId());
                photo.setManuallyEdited(photo.getSuggestedReading() == null
                        || new BigDecimal(photo.getSuggestedReading()).compareTo(confirmed) != 0);
                photo.setNote(request.note() == null ? null : request.note().trim());
                photos.saveAndFlush(photo);
                audit.log(user.getUsername(), user.getRole().name(), "PHOTO_READING_CONFIRM", String.valueOf(id),
                        RequestContext.getRequestId(), "SUCCESS", plain(baseline),
                        plain(confirmed) + ";readingId=" + reading.getId() + ";manuallyEdited=" + photo.getManuallyEdited());
                return dto(photo, meter);
            });
        }
    }

    private <T> T inTransaction(Supplier<T> action) {
        return transaction.execute(status -> action.get());
    }

    private PhotoReading requiredPhoto(Long id) {
        if (id == null || id <= 0) throw bad("照片编号无效");
        return photos.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "照片记录不存在"));
    }

    private Meter authorizedMeter(String id, AuthUser user) {
        if (id == null || id.isBlank()) throw bad("请选择要拍照抄表的水表");
        Meter meter = meters.findByMeterId(id).orElseThrow(() -> new BusinessException(ErrorCode.METER_NOT_FOUND));
        access.requireAccessMeter(user, meter);
        return meter;
    }

    private void requireDraft(PhotoReading photo) {
        if (!"DRAFT".equals(photo.getStatus())) throw conflict("已确认照片不可重新识别或修改");
    }

    private boolean hasActiveTask(String meterId) {
        return tasks.findAllByOrderByIdDesc().stream().anyMatch(task -> {
            if (task.getStatus() != DomainEnums.TaskStatus.执行中 && task.getStatus() != DomainEnums.TaskStatus.补抄中) return false;
            // Legacy active tasks without a target snapshot are conservatively treated as conflicting.
            if (task.getTargetMeterIds() == null || task.getTargetMeterIds().isBlank()) return true;
            return Arrays.stream(task.getTargetMeterIds().split(",")).map(String::trim).anyMatch(meterId::equals);
        });
    }

    private BigDecimal baseline(Meter meter) {
        BigDecimal current = meter.getReading() == null ? BigDecimal.ZERO : meter.getReading();
        Reading latest = readings.findFirstByMeterIdOrderByTsDesc(meter.getMeterId()).orElse(null);
        if (latest != null && latest.getCumulativeFlow() != null) current = current.max(latest.getCumulativeFlow());
        return current;
    }

    private PhotoReadingDto dto(PhotoReadingSnapshot photo, Meter meter) {
        PhotoReadingDto.Crop crop = photo.getCropX() == null ? null : new PhotoReadingDto.Crop(
                photo.getCropX(), photo.getCropY(), photo.getCropWidth(), photo.getCropHeight());
        return new PhotoReadingDto(photo.getId(), photo.getMeterId(), meter.getUserName(), meter.getArea(), photo.getStatus(),
                photo.getCreatedAt(), photo.getUploadedBy(), photo.getImageWidth(), photo.getImageHeight(), photo.getImageMime(),
                plain(baseline(meter)), photo.getRawText(), candidates(photo.getCandidatesJson()), photo.getSuggestedReading(),
                photo.getConfidence(), photo.getEngine(), photo.getOcrError(), photo.getRotation(), crop, photo.getRecognizedAt(),
                photo.getConfirmedReading(), photo.getConfirmedPreviousReading(), photo.getConfirmedBy(), photo.getConfirmedAt(),
                Boolean.TRUE.equals(photo.getManuallyEdited()), photo.getNote(), photo.getReadingId());
    }

    private PhotoReadingDto dto(PhotoReading photo, Meter meter) {
        PhotoReadingDto.Crop crop = photo.getCropX() == null ? null : new PhotoReadingDto.Crop(
                photo.getCropX(), photo.getCropY(), photo.getCropWidth(), photo.getCropHeight());
        return new PhotoReadingDto(photo.getId(), photo.getMeterId(), meter.getUserName(), meter.getArea(), photo.getStatus(),
                photo.getCreatedAt(), photo.getUploadedBy(), photo.getImageWidth(), photo.getImageHeight(), photo.getImageMime(),
                plain(baseline(meter)), photo.getRawText(), candidates(photo.getCandidatesJson()), photo.getSuggestedReading(),
                photo.getConfidence(), photo.getEngine(), photo.getOcrError(), photo.getRotation(), crop, photo.getRecognizedAt(),
                photo.getConfirmedReading(), photo.getConfirmedPreviousReading(), photo.getConfirmedBy(), photo.getConfirmedAt(),
                Boolean.TRUE.equals(photo.getManuallyEdited()), photo.getNote(), photo.getReadingId());
    }

    private Recognition recognizeImage(BufferedImage image) {
        PhotoOcrEngine.OcrResult result;
        try { result = ocr.recognize(image); }
        catch (RuntimeException | LinkageError error) {
            return new Recognition("", List.of(), null, "unavailable", "本地识别引擎不可用，请人工填写并核对读数");
        }
        if (result == null) return new Recognition("", List.of(), null, "unavailable", "本地识别未返回结果，请人工填写读数");
        List<String> values = result.candidates() == null ? List.of() : result.candidates().stream()
                .filter(Objects::nonNull).map(String::trim).filter(value -> DECIMAL.matcher(value).matches())
                .map(value -> plain(new BigDecimal(value))).distinct().limit(10).toList();
        Double confidence = result.confidence();
        if (confidence != null && (!Double.isFinite(confidence) || confidence < 0 || confidence > 100)) confidence = null;
        return new Recognition(limit(result.rawText(), 16000), values, confidence,
                result.engine() == null ? "unavailable" : limit(result.engine(), 200), limit(result.error(), 2000));
    }

    private void applyRecognition(PhotoReading photo, Recognition result, int rotation, PhotoReadingDto.Crop crop) {
        photo.setRawText(result.rawText() == null ? "" : result.rawText());
        try { photo.setCandidatesJson(mapper.writeValueAsString(result.candidates())); }
        catch (IOException error) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "识别结果保存失败"); }
        photo.setSuggestedReading(result.candidates().isEmpty() ? null : result.candidates().get(0));
        photo.setConfidence(result.confidence()); photo.setEngine(result.engine()); photo.setOcrError(result.error());
        photo.setRecognizedAt(LocalDateTime.now()); photo.setRotation(rotation);
        photo.setCropX(crop == null ? null : crop.x()); photo.setCropY(crop == null ? null : crop.y());
        photo.setCropWidth(crop == null ? null : crop.width()); photo.setCropHeight(crop == null ? null : crop.height());
    }

    private List<String> candidates(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return mapper.readValue(json, new TypeReference<List<String>>() {}); }
        catch (IOException error) { return List.of(); }
    }

    private byte[] readUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw bad("请选择非空 JPEG 或 PNG 照片");
        if (file.getSize() > MAX_FILE_BYTES) throw new BusinessException(ErrorCode.UPLOAD_TOO_LARGE);
        try (InputStream stream = file.getInputStream()) {
            byte[] bytes = stream.readNBytes(MAX_FILE_BYTES + 1);
            if (bytes.length > MAX_FILE_BYTES) throw new BusinessException(ErrorCode.UPLOAD_TOO_LARGE);
            return bytes;
        } catch (IOException error) { throw bad("照片读取失败，请重新上传"); }
    }

    private BufferedImage decode(byte[] bytes) {
        boolean jpeg = bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
        boolean png = bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8),
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        if (!jpeg && !png) throw bad("实际图片格式须为 JPEG 或 PNG，不能只修改文件后缀");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> available = ImageIO.getImageReaders(input);
            if (!available.hasNext()) throw bad("照片格式无效或图片已损坏");
            ImageReader reader = available.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(format.equals("jpeg") || format.equals("jpg") || format.equals("png"))) throw bad("仅支持 JPEG 或 PNG 照片");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS)
                    throw bad("照片分辨率不得超过1200万像素，请降低分辨率后上传");
                BufferedImage decoded = reader.read(0);
                if (decoded == null) throw bad("照片解码失败");
                // Fresh raster strips EXIF, embedded thumbnails, comments and original file metadata.
                BufferedImage clean = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = clean.createGraphics();
                try { graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, width, height); graphics.drawImage(decoded, 0, 0, null); }
                finally { graphics.dispose(); }
                return clean;
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException error) { throw bad("照片解码失败，图片可能已损坏"); }
    }

    private byte[] encodePng(BufferedImage image) {
        try (var output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) throw bad("图片规范化失败");
            if (output.size() > MAX_FILE_BYTES) throw bad("规范化后的照片超过8 MB，请降低分辨率或裁剪后重新上传");
            return output.toByteArray();
        } catch (IOException error) { throw bad("图片规范化失败，请重新上传"); }
    }

    private BufferedImage rotate(BufferedImage image, int rotation) {
        if (rotation == 0) return image;
        int width = image.getWidth(), height = image.getHeight();
        BufferedImage rotated = new BufferedImage(rotation == 180 ? width : height,
                rotation == 180 ? height : width, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rotated.createGraphics();
        try {
            switch (rotation) {
                case 90 -> { graphics.translate(height, 0); graphics.rotate(Math.PI / 2); }
                case 180 -> { graphics.translate(width, height); graphics.rotate(Math.PI); }
                case 270 -> { graphics.translate(0, width); graphics.rotate(-Math.PI / 2); }
                default -> throw bad("旋转角度无效");
            }
            graphics.drawImage(image, 0, 0, null);
        } finally { graphics.dispose(); }
        return rotated;
    }

    private BufferedImage crop(BufferedImage image, PhotoReadingDto.Crop crop) {
        if (crop.x() == null || crop.y() == null || crop.width() == null || crop.height() == null
                || crop.x() < 0 || crop.y() < 0 || crop.width() <= 0 || crop.height() <= 0
                || (long) crop.x() + crop.width() > image.getWidth() || (long) crop.y() + crop.height() > image.getHeight()
                || (long) crop.width() * crop.height() > MAX_PIXELS) throw bad("裁剪范围须位于旋转后的图片内且宽高大于0");
        BufferedImage cropped = new BufferedImage(crop.width(), crop.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = cropped.createGraphics();
        try { graphics.drawImage(image, 0, 0, crop.width(), crop.height(), crop.x(), crop.y(),
                crop.x() + crop.width(), crop.y() + crop.height(), null); }
        finally { graphics.dispose(); }
        return cropped;
    }

    private BigDecimal decimal(String value, String field) {
        if (value == null || !DECIMAL.matcher(value).matches()) throw bad(field + "须为最多9位整数和3位小数的非负十进制字符串");
        return new BigDecimal(value);
    }

    private String plain(BigDecimal number) { return number.stripTrailingZeros().toPlainString(); }
    private String limit(String value, int maximum) { return value == null ? null : value.substring(0, Math.min(value.length(), maximum)); }
    private BusinessException bad(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
    private BusinessException conflict(String message) { return new BusinessException(ErrorCode.CONFLICT, message); }
    private record Recognition(String rawText, List<String> candidates, Double confidence, String engine, String error) {}
}
