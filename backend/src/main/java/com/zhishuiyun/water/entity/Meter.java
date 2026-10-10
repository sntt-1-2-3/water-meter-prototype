package com.zhishuiyun.water.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "meter")
public class Meter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 水表编号（业务唯一键，A居民/B商业/C工业 + 序号） */
    @Column(unique = true, nullable = false, length = 32)
    private String meterId;

    /** 用户名称 */
    private String userName;

    /** 所属区域 */
    private String area;

    @Enumerated(EnumType.STRING)
    private DomainEnums.MeterType type;

    /** 累计读数（本期） */
    private BigDecimal reading;

    /** 上期读数 */
    private BigDecimal lastReading;

    @Enumerated(EnumType.STRING)
    private DomainEnums.MeterStatus status;

    /** 安装时间 */
    private LocalDateTime installTime;

    /** 最近一次上报/抄表时间 */
    private LocalDateTime updatedAt;
}
