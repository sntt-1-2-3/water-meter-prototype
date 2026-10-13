package com.zhishuiyun.water.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "user_feedback", indexes = {@Index(columnList = "username"), @Index(columnList = "area")})
public class UserFeedback {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String username;
    private String displayName;
    @Column(nullable = false)
    private String meterId;
    private String area;
    @Column(nullable = false, length = 60)
    private String type;
    @Column(nullable = false, length = 2000)
    private String description;
    @Column(nullable = false)
    private String status;
    @Column(length = 2000)
    private String resolution;
    private String handledBy;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
