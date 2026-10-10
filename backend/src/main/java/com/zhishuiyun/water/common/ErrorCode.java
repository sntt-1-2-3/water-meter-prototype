package com.zhishuiyun.water.common;

import lombok.Getter;

/**
 * 统一业务错误码。code 为稳定字符串，前端/测试据此断言；
 * httpStatus 为对应的 HTTP 状态码。
 */
@Getter
public enum ErrorCode {

    // ---- 通用 ----
    OK("OK", 200, "成功"),
    BAD_REQUEST("BAD_REQUEST", 400, "请求参数错误"),
    VALIDATION_FAILED("VALIDATION_FAILED", 400, "参数校验失败"),
    UNAUTHORIZED("UNAUTHORIZED", 401, "未登录或会话已过期"),
    FORBIDDEN("FORBIDDEN", 403, "无权限执行该操作"),
    NOT_FOUND("NOT_FOUND", 404, "资源不存在"),
    INTERNAL_ERROR("INTERNAL_ERROR", 500, "服务器内部错误"),

    // ---- 鉴权 ----
    BAD_CREDENTIALS("BAD_CREDENTIALS", 401, "用户名或密码错误"),
    TOKEN_EXPIRED("TOKEN_EXPIRED", 401, "会话已过期，请重新登录"),
    ACCOUNT_DISABLED("ACCOUNT_DISABLED", 403, "账号已被禁用"),

    // ---- 数据质量（上报接口 DQ-001 ~ DQ-025）----
    METER_ID_REQUIRED("METER_ID_REQUIRED", 400, "缺少表具编号"),
    METER_ID_FORMAT_INVALID("METER_ID_FORMAT_INVALID", 400, "表具编号格式错误"),
    METER_NOT_FOUND("METER_NOT_FOUND", 400, "表具编号不存在"),
    INSTANT_FLOW_REQUIRED("INSTANT_FLOW_REQUIRED", 400, "缺少瞬时流量"),
    CUMULATIVE_FLOW_REQUIRED("CUMULATIVE_FLOW_REQUIRED", 400, "缺少累计流量"),
    TEMPERATURE_REQUIRED("TEMPERATURE_REQUIRED", 400, "缺少供水温度"),
    INSTANT_FLOW_NEGATIVE("INSTANT_FLOW_NEGATIVE", 400, "瞬时流量不能为负"),
    CUMULATIVE_FLOW_NEGATIVE("CUMULATIVE_FLOW_NEGATIVE", 400, "累计流量不能为负"),
    CUMULATIVE_ROLLBACK("CUMULATIVE_ROLLBACK", 400, "累计流量倒退"),
    TIMESTAMP_REQUIRED("TIMESTAMP_REQUIRED", 400, "缺少时间戳"),
    TIMESTAMP_INVALID("TIMESTAMP_INVALID", 400, "时间戳格式非法"),
    TIMESTAMP_OUT_OF_RANGE("TIMESTAMP_OUT_OF_RANGE", 400, "时间戳超出合理范围"),
    TIMESTAMP_BEFORE_INSTALL("TIMESTAMP_BEFORE_INSTALL", 400, "上报时间早于安装时间"),
    TIMESTAMP_CONFLICT("TIMESTAMP_CONFLICT", 400, "同一时间戳上报数据冲突"),
    TEMPERATURE_OUT_OF_RANGE("TEMPERATURE_OUT_OF_RANGE", 400, "水温超出合理范围"),
    VALVE_STATUS_INVALID("VALVE_STATUS_INVALID", 400, "阀门状态非法"),
    ALARM_CODE_INVALID("ALARM_CODE_INVALID", 400, "报警码非法"),
    ALARM_VALVE_CONFLICT("ALARM_VALVE_CONFLICT", 400, "报警码与阀门状态冲突"),
    DUPLICATE_REPORT("DUPLICATE_REPORT", 409, "重复上报"),
    REPORT_FREQUENCY_HIGH("REPORT_FREQUENCY_HIGH", 429, "上报频率过高"),
    FLOW_INCONSISTENT("FLOW_INCONSISTENT", 422, "瞬时流量与累计增量不一致"),
    FIELD_ORDER_INVALID("FIELD_ORDER_INVALID", 400, "字段顺序错误"),

    // ---- 计费 / 抄表 / 业务 ----
    METER_ID_DUPLICATE("METER_ID_DUPLICATE", 409, "水表编号已存在"),
    BILL_ALREADY_EXISTS("BILL_ALREADY_EXISTS", 409, "该账期账单已存在，不可重复生成"),
    BILL_NOT_READY("BILL_NOT_READY", 422, "存在缺抄或非法读数，无法生成账单"),
    READING_MISSING("READING_MISSING", 422, "缺少有效抄见量"),
    DUPLICATE_TASK("DUPLICATE_TASK", 409, "任务已存在，不可重复启动"),
    PRICE_RULE_NOT_FOUND("PRICE_RULE_NOT_FOUND", 404, "未找到对应水价规则"),
    RULE_MODIFY_FORBIDDEN("RULE_MODIFY_FORBIDDEN", 403, "无权修改水价规则"),

    // ---- AI ----
    AI_SERVICE_UNAVAILABLE("AI_SERVICE_UNAVAILABLE", 503, "AI 服务暂时不可用，请稍后重试");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(String code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}
