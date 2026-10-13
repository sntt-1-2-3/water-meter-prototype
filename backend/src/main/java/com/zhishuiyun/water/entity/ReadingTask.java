package com.zhishuiyun.water.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 抄表任务。同周期唯一有效任务，保证幂等；漏抄/失败走重试与异常。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reading_task")
public class ReadingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 任务名 */
    private String name;

    /** 覆盖范围 */
    private String scope;

    /** 账期，如 2026-09（幂等键） */
    private String period;

    /** 覆盖总数 */
    private Integer total;

    /** 成功数 */
    @Builder.Default
    private Integer success = 0;

    /** 漏抄数 */
    @Builder.Default
    private Integer missed = 0;

    /** 失败数 */
    @Builder.Default
    private Integer failed = 0;

    /** 重试次数 */
    @Builder.Default
    private Integer retryCount = 0;

    @Enumerated(EnumType.STRING)
    private DomainEnums.TaskStatus status;

    /** 进度 0-100 */
    @Builder.Default
    private Integer progress = 0;

    /** 计划开始时间 */
    private LocalDateTime planTime;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String createdBy;
    private String createdRole;
    private String areaScope;
    private String executionBy;
    private Boolean systemTask;
    @Column(length = 12000)
    private String targetMeterIds;
    @Column(length = 12000)
    private String completedMeterIds;
    @Column(length = 12000)
    private String failedMeterIds;
    @Column(length = 12000)
    private String retryAttemptedMeterIds;
    private Boolean simulateFailure;
    private Boolean failureInjected;
}
