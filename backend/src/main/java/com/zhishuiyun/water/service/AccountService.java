package com.zhishuiyun.water.service;

import com.zhishuiyun.water.common.BusinessException;
import com.zhishuiyun.water.common.ErrorCode;
import com.zhishuiyun.water.common.RequestContext;
import com.zhishuiyun.water.dto.AccountProfile;
import com.zhishuiyun.water.entity.Account;
import com.zhishuiyun.water.entity.DomainEnums.AccountRole;
import com.zhishuiyun.water.repository.AccountRepository;
import com.zhishuiyun.water.repository.MeterRepository;
import com.zhishuiyun.water.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private final MeterRepository meterRepository;
    private final AccessPolicy accessPolicy;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<AccountProfile> list(AuthUser user) {
        accessPolicy.requireAdmin(user);
        return accountRepository.findAll().stream().map(AccountProfile::from).toList();
    }

    @Transactional
    public AccountProfile bind(Long id, String requestedMeterId, AuthUser user) {
        accessPolicy.requireAdmin(user);
        Account account = accountRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (account.getRole() != AccountRole.USER) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅普通用户账号需要绑定水表");
        }
        String meterId = requestedMeterId == null || requestedMeterId.isBlank() ? null : requestedMeterId.trim();
        if (meterId != null) {
            meterRepository.findByMeterId(meterId).orElseThrow(() -> new BusinessException(ErrorCode.METER_NOT_FOUND));
            boolean claimed = accountRepository.findAll().stream().anyMatch(other -> !other.getId().equals(id)
                    && meterId.equals(other.getBoundMeterId()));
            if (claimed) throw new BusinessException(ErrorCode.BAD_REQUEST, "该水表已绑定其他用户，请先解除原绑定");
        }
        String previous = account.getBoundMeterId();
        account.setBoundMeterId(meterId);
        accountRepository.save(account);
        notificationService.notifyUser(account.getUsername(), "水表绑定已更新", meterId == null
                ? "管理员已解除水表绑定，如有疑问请联系管理员。" : "管理员已将水表 " + meterId + " 绑定到您的账号，现在可查看本人用水与账单。", "/user/home");
        auditService.log(user.getUsername(), user.getRole().name(), "BIND_METER", account.getUsername(),
                RequestContext.getRequestId(), "SUCCESS", previous, meterId);
        return AccountProfile.from(account);
    }
}
