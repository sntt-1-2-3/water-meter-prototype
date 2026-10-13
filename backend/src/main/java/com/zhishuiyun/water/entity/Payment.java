package com.zhishuiyun.water.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "payment", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"payer", "idempotencyKey"}),
        @UniqueConstraint(columnNames = "billItemId")})
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String transactionNo;
    @Column(nullable = false)
    private Long billItemId;
    private String meterId;
    @Column(nullable = false)
    private String payer;
    @Column(nullable = false, length = 100)
    private String idempotencyKey;
    private BigDecimal amount;
    private LocalDateTime paidAt;
    private String channel;
}
