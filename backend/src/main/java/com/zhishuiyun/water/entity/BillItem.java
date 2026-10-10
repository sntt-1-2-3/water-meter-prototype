package com.zhishuiyun.water.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账单明细，一行一户一账期，是计费与缴费幂等的核心单元。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bill_item",
        indexes = {@Index(columnList = "batchNo"), @Index(columnList = "meterId")})
public class BillItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属批次号 */
    private String batchNo;

    /** 账期 */
    private String period;

    private String meterId;

    private String userName;

    private String area;

    @Enumerated(EnumType.STRING)
    private DomainEnums.MeterType meterType;

    /** 本期用量 m³ */
    private BigDecimal usage;

    /** 水费（已四舍五入到分） */
    private BigDecimal waterFee;

    /** 违约金 */
    private BigDecimal penalty;

    /** 应缴合计 = 水费 + 违约金 */
    private BigDecimal totalDue;

    /** 已缴金额 */
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /** 逾期天数 */
    @Builder.Default
    private Integer overdueDays = 0;

    @Enumerated(EnumType.STRING)
    private DomainEnums.BillStatus status;

    private LocalDateTime paidAt;
}
