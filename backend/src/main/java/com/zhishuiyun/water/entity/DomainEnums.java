package com.zhishuiyun.water.entity;

/**
 * 领域枚举。枚举 name 直接使用与前端 JSON 对齐的中文取值，
 * JPA 以 STRING 存储、Jackson 以 name 输出，前后端口径一致。
 */
public final class DomainEnums {

    private DomainEnums() {
    }

    /** 水表用户类型 */
    public enum MeterType { 居民, 商业, 工业 }

    /** 水表在线状态 */
    public enum MeterStatus { 在线, 异常, 离线 }

    /** 异常风险等级 */
    public enum RiskLevel { 高, 中, 低 }

    /** 异常处置状态机 */
    public enum AnomalyStatus { 待处理, 处理中, 已完成 }

    /** 阀门状态 */
    public enum ValveStatus { 开启, 关闭 }

    /** 账号角色 */
    public enum AccountRole { ADMIN, OPERATOR, FINANCE, AUDITOR, USER }

    /** 抄表任务状态 */
    public enum TaskStatus { 待执行, 执行中, 补抄中, 失败待重试, 已完成 }

    /** 账单缴费状态 */
    public enum BillStatus { 待缴, 部分缴费, 已缴, 逾期 }
}
