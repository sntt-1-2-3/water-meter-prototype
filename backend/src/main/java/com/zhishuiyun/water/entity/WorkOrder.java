package com.zhishuiyun.water.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 工单：异常派单 / 用户反馈的处置载体。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "work_order")
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联异常编号或反馈单号 */
    private String refNo;

    private String title;

    private String description;

    /** 优先级 */
    private String priority;

    /** 负责人 */
    private String assignee;

    /** 建议动作 */
    private String action;

    @Enumerated(EnumType.STRING)
    private DomainEnums.AnomalyStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime closedAt;
}
