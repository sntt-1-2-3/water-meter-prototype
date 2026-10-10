package com.zhishuiyun.water.service;

import com.zhishuiyun.water.common.ErrorCode;
import com.zhishuiyun.water.common.BusinessException;
import com.zhishuiyun.water.dto.LoginResponse;
import com.zhishuiyun.water.entity.Account;
import com.zhishuiyun.water.repository.AccountRepository;
import com.zhishuiyun.water.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(String username, String password) {
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_CREDENTIALS));
        if (!Boolean.TRUE.equals(account.getEnabled())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(password, account.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }
        String token = jwtService.generate(account.getUsername(), account.getRole(), account.getName());
        return new LoginResponse(token, account.getUsername(), account.getName(), account.getRole().name());
    }
}
