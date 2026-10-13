package com.zhishuiyun.water.dto;

import com.zhishuiyun.water.entity.Account;

public record AccountProfile(Long id, String username, String name, String phone, String role,
                             String areaScope, String boundMeterId, Boolean enabled) {
    public static AccountProfile from(Account account) {
        return new AccountProfile(account.getId(), account.getUsername(), account.getName(), account.getPhone(),
                account.getRole().name(), account.getAreaScope(), account.getBoundMeterId(), account.getEnabled());
    }
}
