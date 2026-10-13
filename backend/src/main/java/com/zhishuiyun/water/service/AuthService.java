package com.zhishuiyun.water.service;

import com.zhishuiyun.water.common.ErrorCode;
import com.zhishuiyun.water.common.BusinessException;
import com.zhishuiyun.water.dto.LoginResponse;
import com.zhishuiyun.water.dto.RegisterRequest;
import com.zhishuiyun.water.dto.AccountProfile;
import com.zhishuiyun.water.entity.Account;
import com.zhishuiyun.water.entity.DomainEnums.AccountRole;
import com.zhishuiyun.water.repository.AccountRepository;
import com.zhishuiyun.water.security.AuthUser;
import com.zhishuiyun.water.security.JwtService;
import com.zhishuiyun.water.common.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public LoginResponse login(String username, String password) {
        if (username == null || password == null || username.trim().length() > 40
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }
        Account account = accountRepository.findByUsername(username.trim()).orElse(null);
        if (account == null) {
            auditService.log(username.trim(), "UNKNOWN", "LOGIN", username.trim(), RequestContext.getRequestId(), "FAILED", null, null);
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }
        if (!Boolean.TRUE.equals(account.getEnabled())) {
            auditService.log(account.getUsername(), account.getRole().name(), "LOGIN", account.getUsername(), RequestContext.getRequestId(), "FAILED", null, null);
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(password, account.getPassword())) {
            auditService.log(account.getUsername(), account.getRole().name(), "LOGIN", account.getUsername(), RequestContext.getRequestId(), "FAILED", null, null);
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }
        auditService.log(account.getUsername(), account.getRole().name(), "LOGIN", account.getUsername(), RequestContext.getRequestId(), "SUCCESS", null, null);
        return response(account);
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (accountRepository.findByUsername(username).isPresent()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名已存在");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "密码的UTF-8长度不能超过72字节");
        }
        Account account = Account.builder().username(username).name(request.name().trim())
                .phone(request.phone() == null ? null : request.phone().trim())
                .password(passwordEncoder.encode(request.password())).role(AccountRole.USER)
                .areaScope(null).boundMeterId(null).enabled(true).build();
        try {
            account = accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名已存在");
        }
        notificationService.notifyUser(username, "欢迎使用智水云", "注册成功，请联系管理员核验身份并绑定水表。绑定前不会展示其他住户的数据。", "/user/home");
        notificationService.notifyAdministrators("新用户等待绑定水表", account.getName() + "（" + username + "）已注册，请核验用户身份后绑定水表。", "/admin/accounts");
        auditService.log(username, AccountRole.USER.name(), "REGISTER", username, RequestContext.getRequestId(), "SUCCESS", null, null);
        return response(account);
    }

    public AccountProfile profile(AuthUser user) {
        return AccountProfile.from(accountRepository.findByUsername(user.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED)));
    }

    private LoginResponse response(Account account) {
        String token = jwtService.generate(account.getUsername(), account.getRole(), account.getName());
        return new LoginResponse(token, account.getUsername(), account.getName(), account.getRole().name(),
                account.getId(), account.getPhone(), account.getAreaScope(), account.getBoundMeterId());
    }
}
