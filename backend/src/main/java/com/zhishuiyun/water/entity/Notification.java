package com.zhishuiyun.water.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notification", indexes = @Index(columnList = "recipient"))
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String recipient;
    @Column(nullable = false, length = 150)
    private String title;
    @Column(nullable = false, length = 4000)
    private String content;
    private String route;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
