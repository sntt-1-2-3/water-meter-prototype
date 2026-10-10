package com.zhishuiyun.water.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 审计日志（不可删除，只增不改）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_log",
        indexes = {@Index(columnList = "operator"), @Index(columnList = "ts")})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作者登录名 */
    private String operator;

    /** 操作者角色 */
    private String role;

    /** 动作，如 CREATE_METER / BILL_EXPORT */
    private String action;

    /** 操作对象，如 水表 A-00218 */
    private String target;

    /** 请求追踪号 */
    private String requestId;

    /** 结果 SUCCESS / FAILED */
    private String result;

    /** 旧值快照（可选，脱敏） */
    @Column(length = 2000)
    private String oldValue;

    /** 新值快照（可选，脱敏） */
    @Column(length = 2000)
    private String newValue;

    private LocalDateTime ts;
}
