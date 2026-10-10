package com.zhishuiyun.water.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账单批次（聚合），对应前端 BillBatch。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bill")
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 批次号，如 2026-09-居民（唯一） */
    @Column(unique = true, nullable = false)
    private String batchNo;

    /** 账期，如 2026-09 */
    private String period;

    /** 用户类型 */
    @Enumerated(EnumType.STRING)
    private DomainEnums.MeterType meterType;

    /** 用户数 */
    private Integer userCount;

    /** 应收合计 */
    private BigDecimal totalAmount;

    /** 已收合计 */
    private BigDecimal totalPaid;

    /** 批次状态，如 收费中 / 已关账 */
    private String status;

    private LocalDateTime createdAt;
}
