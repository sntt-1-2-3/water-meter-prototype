package com.zhishuiyun.water.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PhotoReadingDto(
        Long id, String meterId, String meterUserName, String area, String status,
        LocalDateTime createdAt, String uploadedBy, Integer imageWidth, Integer imageHeight, String imageMime,
        String previousReading, String rawText, List<String> candidates, String suggestedReading,
        Double confidence, String engine, String ocrError, Integer rotation, Crop crop,
        LocalDateTime recognizedAt, String confirmedReading, String confirmedPreviousReading,
        String confirmedBy, LocalDateTime confirmedAt, boolean manuallyEdited, String note, Long readingId) {
    /** Coordinates refer to the full rotated image, before cropping. */
    public record Crop(
            @JsonDeserialize(using = PhotoIntegerDeserializer.class) Integer x,
            @JsonDeserialize(using = PhotoIntegerDeserializer.class) Integer y,
            @JsonDeserialize(using = PhotoIntegerDeserializer.class) Integer width,
            @JsonDeserialize(using = PhotoIntegerDeserializer.class) Integer height) {}
}
