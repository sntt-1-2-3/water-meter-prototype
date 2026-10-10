package com.zhishuiyun.water.common;

import java.util.UUID;

/**
 * 请求上下文：承载 requestId，供统一响应与审计日志使用。
 */
public final class RequestContext {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void setRequestId(String requestId) {
        REQUEST_ID.set(requestId);
    }

    public static String getRequestId() {
        String id = REQUEST_ID.get();
        return id != null ? id : "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static void clear() {
        REQUEST_ID.remove();
    }
}
