package com.zhishuiyun.water.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 从 SecurityContext 读取当前登录用户。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthUser> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AuthUser required() {
        return get().orElseThrow(() -> new com.zhishuiyun.water.common.BusinessException(
                com.zhishuiyun.water.common.ErrorCode.UNAUTHORIZED));
    }
}
