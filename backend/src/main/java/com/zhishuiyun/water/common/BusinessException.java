package com.zhishuiyun.water.common;

import lombok.Getter;

/**
 * 业务异常，携带统一错误码；由 GlobalExceptionHandler 转为统一响应。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
