package com.zhishuiyun.water.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 水价规则（可配置）。居民三档、商业统一价、工业合同价。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "price_rule")
public class PriceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 规则版本号 */
    private Integer version;

    /** 适用类型：居民 / 商业 / 工业 */
    @Enumerated(EnumType.STRING)
    private DomainEnums.MeterType meterType;

    /** 阶梯档位序号（居民 1/2/3；商业工业为 1） */
    private Integer tier;

    /** 该档起始用量（含） */
    private BigDecimal fromUsage;

    /** 该档结束用量（不含），null 表示无上限 */
    private BigDecimal toUsage;

    /** 单价 元/m³ */
    private BigDecimal unitPrice;

    /** 违约金日费率，如 0.0005 */
    private BigDecimal penaltyRate;

    /** 生效时间 */
    private LocalDateTime effectiveFrom;
}
