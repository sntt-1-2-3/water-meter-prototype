package com.zhishuiyun.water.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 原始上报数据（水表 → 平台），对应测试数据的「原始上报数据」工作表。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reading",
        indexes = {@Index(columnList = "meterId"), @Index(columnList = "ts")})
public class Reading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 表具编号 */
    @Column(nullable = false, length = 32)
    private String meterId;

    /** 时间戳 */
    @Column(nullable = false)
    private LocalDateTime ts;

    /** 瞬时流量 m³/h */
    private BigDecimal instantFlow;

    /** 累计流量 m³ */
    private BigDecimal cumulativeFlow;

    /** 水温 ℃ */
    private BigDecimal temperature;

    @Enumerated(EnumType.STRING)
    private DomainEnums.ValveStatus valve;

    /** 报警码，0 表示正常 */
    @Column(length = 16)
    private String alarmCode;
}
