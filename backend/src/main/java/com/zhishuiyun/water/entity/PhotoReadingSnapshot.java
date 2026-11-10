package com.zhishuiyun.water.entity;

import java.time.LocalDateTime;

/** Metadata-only projection: paged listings must not fetch up to 50 image BLOBs. */
public interface PhotoReadingSnapshot {
    Long getId();
    String getMeterId();
    String getStatus();
    LocalDateTime getCreatedAt();
    String getUploadedBy();
    Integer getImageWidth();
    Integer getImageHeight();
    String getImageMime();
    String getRawText();
    String getCandidatesJson();
    String getSuggestedReading();
    Double getConfidence();
    String getEngine();
    String getOcrError();
    Integer getRotation();
    Integer getCropX();
    Integer getCropY();
    Integer getCropWidth();
    Integer getCropHeight();
    LocalDateTime getRecognizedAt();
    String getConfirmedReading();
    String getConfirmedPreviousReading();
    String getConfirmedBy();
    LocalDateTime getConfirmedAt();
    Boolean getManuallyEdited();
    String getNote();
    Long getReadingId();
}
