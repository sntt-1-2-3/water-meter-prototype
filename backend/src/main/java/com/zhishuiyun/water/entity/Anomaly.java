package com.zhishuiyun.water.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 异常事件。状态机：待处理 → 处理中 → 已完成。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "anomaly",
        indexes = {@Index(columnList = "meterId"), @Index(columnList = "level")})
public class Anomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 异常编号，如 AL-260919-038 */
    @Column(unique = true, nullable = false)
    private String anomalyId;

    private String meterId;

    private String area;

    /** 异常类型，如 持续高流量 */
    private String type;

    @Enumerated(EnumType.STRING)
    private DomainEnums.RiskLevel level;

    /** 触发时间 */
    private LocalDateTime time;

    /** 报警码，如 E01 */
    private String alarmCode;

    /** 建议动作 */
    private String suggestion;

    @Enumerated(EnumType.STRING)
    private DomainEnums.AnomalyStatus status;

    /** 确认人 */
    private String confirmedBy;

    private LocalDateTime confirmedAt;
}
