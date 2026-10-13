package com.zhishuiyun.water.security;

import com.zhishuiyun.water.entity.DomainEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 当前登录用户信息，作为 SecurityContext 的 principal。
 */
@Getter
@Builder
@AllArgsConstructor
public class AuthUser {

    private final String username;
    private final String name;
    private final DomainEnums.AccountRole role;
    /** 运营人员限定的区域；为空时无辖区权限。 */
    private final String areaScope;
    /** 普通用户绑定的水表编号 */
    private final String boundMeterId;

    public boolean isAdmin() {
        return role == DomainEnums.AccountRole.ADMIN;
    }

    public boolean isAuditor() {
        return role == DomainEnums.AccountRole.AUDITOR;
    }

    public boolean isUser() {
        return role == DomainEnums.AccountRole.USER;
    }

    /** 判断某区域是否在当前用户数据范围内（areaScope 支持逗号分隔多区域） */
    public boolean canAccessArea(String area) {
        if (role == DomainEnums.AccountRole.ADMIN || role == DomainEnums.AccountRole.FINANCE
                || role == DomainEnums.AccountRole.AUDITOR) {
            return true;
        }
        // 普通用户必须按绑定的具体水表判断，不能把空 areaScope 当作全区域权限。
        if (role != DomainEnums.AccountRole.OPERATOR || areaScope == null || areaScope.isBlank() || area == null) {
            return false;
        }
        for (String a : areaScope.split(",")) {
            if (a.trim().equals(area)) {
                return true;
            }
        }
        return false;
    }

    /** 数据范围内的区域列表，逗号分隔；null 表示全部。 */
    public java.util.List<String> areaList() {
        if (role == DomainEnums.AccountRole.OPERATOR && (areaScope == null || areaScope.isBlank())) {
            return java.util.List.of();
        }
        if (areaScope == null || areaScope.isBlank()) {
            return null;
        }
        return java.util.Arrays.stream(areaScope.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).toList();
    }
}
