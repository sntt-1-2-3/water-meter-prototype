package com.zhishuiyun.water.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A private photograph draft. Only explicit confirmation may create a Reading. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "photo_reading", indexes = {@Index(columnList = "meterId"), @Index(columnList = "createdAt")})
public class PhotoReading {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 32)
    private String meterId;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    private String uploadedBy;

    // Deliberately not @Lob: SQLite JDBC does not implement Connection.createBlob().
    @Column(nullable = false, columnDefinition = "BLOB")
    private byte[] imageBytes;
    private Integer imageWidth;
    private Integer imageHeight;
    @Column(length = 32)
    private String imageMime;

    @Column(columnDefinition = "TEXT")
    private String rawText;
    @Column(columnDefinition = "TEXT")
    private String candidatesJson;
    @Column(length = 32)
    private String suggestedReading;
    private Double confidence;
    private String engine;
    @Column(length = 2000)
    private String ocrError;
    private Integer rotation;
    private Integer cropX;
    private Integer cropY;
    private Integer cropWidth;
    private Integer cropHeight;
    private LocalDateTime recognizedAt;

    @Column(length = 32)
    private String confirmedReading;
    @Column(length = 32)
    private String confirmedPreviousReading;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private Boolean manuallyEdited;
    @Column(length = 2000)
    private String note;
    @Column(unique = true)
    private Long readingId;
}
