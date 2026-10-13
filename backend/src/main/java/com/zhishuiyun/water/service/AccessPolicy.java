package com.zhishuiyun.water.service;

import com.zhishuiyun.water.common.BusinessException;
import com.zhishuiyun.water.common.ErrorCode;
import com.zhishuiyun.water.entity.DomainEnums.AccountRole;
import com.zhishuiyun.water.entity.Meter;
import com.zhishuiyun.water.repository.MeterRepository;
import com.zhishuiyun.water.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 所有业务服务共用的角色与水表数据范围规则。 */
@Service
@RequiredArgsConstructor
public class AccessPolicy {
    private final MeterRepository meterRepository;

    public void requireAdmin(AuthUser user) {
        require(user != null && user.getRole() == AccountRole.ADMIN);
    }

    public void requireManageMeters(AuthUser user) {
        require(isAdminOrOperator(user));
    }

    public void requireReadings(AuthUser user) {
        requireManageMeters(user);
    }

    public void requireManageAnomalies(AuthUser user) {
        requireManageMeters(user);
    }

    public void requireBilling(AuthUser user) {
        require(user != null && (user.getRole() == AccountRole.ADMIN || user.getRole() == AccountRole.FINANCE));
    }

    public boolean canAccessArea(AuthUser user, String area) {
        return user != null && user.canAccessArea(area);
    }

    public boolean canAccessMeter(AuthUser user, Meter meter) {
        if (user == null || user.getRole() == null || meter == null) return false;
        if (user.isUser()) {
            return user.getBoundMeterId() != null && !user.getBoundMeterId().isBlank()
                    && user.getBoundMeterId().equals(meter.getMeterId());
        }
        return canAccessArea(user, meter.getArea());
    }

    public boolean canAccessMeterId(AuthUser user, String meterId) {
        if (meterId == null) return false;
        return meterRepository.findByMeterId(meterId).map(meter -> canAccessMeter(user, meter)).orElse(false);
    }

    public void requireAccessMeter(AuthUser user, Meter meter) {
        require(canAccessMeter(user, meter));
    }

    public List<Meter> visibleMeters(AuthUser user) {
        return meterRepository.findAll().stream().filter(meter -> canAccessMeter(user, meter)).toList();
    }

    private boolean isAdminOrOperator(AuthUser user) {
        return user != null && (user.getRole() == AccountRole.ADMIN || user.getRole() == AccountRole.OPERATOR);
    }

    private void require(boolean allowed) {
        if (!allowed) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
}
