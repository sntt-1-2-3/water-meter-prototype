package com.zhishuiyun.water.service;

import com.zhishuiyun.water.common.BusinessException;
import com.zhishuiyun.water.common.ErrorCode;
import com.zhishuiyun.water.common.RequestContext;
import com.zhishuiyun.water.dto.MeterDto;
import com.zhishuiyun.water.dto.MeterRequest;
import com.zhishuiyun.water.dto.PageResult;
import com.zhishuiyun.water.entity.DomainEnums;
import com.zhishuiyun.water.entity.Meter;
import com.zhishuiyun.water.repository.MeterRepository;
import com.zhishuiyun.water.security.AuthUser;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MeterService {

    private final MeterRepository meterRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResult<MeterDto> page(int page, int pageSize, String keyword, String type, AuthUser user) {
        Specification<Meter> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("meterId"), like),
                        cb.like(root.get("userName"), like),
                        cb.like(root.get("area"), like)));
            }
            if (type != null && !type.isBlank() && !"全部类型".equals(type)) {
                predicates.add(cb.equal(root.get("type"), DomainEnums.MeterType.valueOf(type)));
            }
            applyDataScope(predicates, cb, root, user);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Meter> result = meterRepository.findAll(spec,
                PageRequest.of(Math.max(0, page - 1), pageSize, Sort.by("id").ascending()));
        List<MeterDto> items = result.getContent().stream().map(MeterDto::from).toList();
        return PageResult.of(items, result.getTotalElements(), page, pageSize);
    }

    @Transactional
    public MeterDto create(MeterRequest req, AuthUser user) {
        if (req.meterId() == null || req.meterId().isBlank()) {
            throw new BusinessException(ErrorCode.METER_ID_REQUIRED);
        }
        requireAdminOrOperator(user);
        if (meterRepository.existsByMeterId(req.meterId())) {
            throw new BusinessException(ErrorCode.METER_ID_DUPLICATE);
        }
        String area = req.area();
        if (user.getAreaScope() != null && !user.canAccessArea(area)) {
            throw new BusinessException(ErrorCode.FORBIDDEN); // 运营人员只能在所属区域建档
        }
        Meter meter = Meter.builder()
                .meterId(req.meterId())
                .userName(req.userName())
                .area(area)
                .type(req.type() != null ? req.type() : DomainEnums.MeterType.居民)
                .reading(req.reading() != null ? req.reading() : BigDecimal.ZERO)
                .lastReading(req.lastReading() != null ? req.lastReading() : BigDecimal.ZERO)
                .status(req.status() != null ? req.status() : DomainEnums.MeterStatus.在线)
                .installTime(req.installTime() != null ? req.installTime() : LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Meter saved = meterRepository.save(meter);
        auditService.log(user.getUsername(), user.getRole().name(), "CREATE_METER", saved.getMeterId(),
                RequestContext.getRequestId(), "SUCCESS", null, saved.getMeterId());
        return MeterDto.from(saved);
    }

    @Transactional
    public MeterDto update(String meterId, MeterRequest req, AuthUser user) {
        requireAdminOrOperator(user);
        Meter meter = meterRepository.findByMeterId(meterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.METER_NOT_FOUND));
        if (user.getAreaScope() != null && !user.canAccessArea(meter.getArea())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (req.userName() != null) meter.setUserName(req.userName());
        if (req.area() != null && user.getAreaScope() == null) meter.setArea(req.area());
        if (req.type() != null) meter.setType(req.type());
        if (req.reading() != null) meter.setReading(req.reading());
        if (req.lastReading() != null) meter.setLastReading(req.lastReading());
        if (req.status() != null) meter.setStatus(req.status());
        meter.setUpdatedAt(LocalDateTime.now());
        Meter saved = meterRepository.save(meter);
        auditService.log(user.getUsername(), user.getRole().name(), "UPDATE_METER", meterId,
                RequestContext.getRequestId(), "SUCCESS", null, meterId);
        return MeterDto.from(saved);
    }

    @Transactional(readOnly = true)
    public Meter findByMeterId(String meterId) {
        return meterRepository.findByMeterId(meterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.METER_NOT_FOUND));
    }

    private void requireAdminOrOperator(AuthUser user) {
        if (user.getRole() != DomainEnums.AccountRole.ADMIN
                && user.getRole() != DomainEnums.AccountRole.OPERATOR) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void applyDataScope(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb,
                                jakarta.persistence.criteria.Root<Meter> root, AuthUser user) {
        if (user.areaList() != null) {
            predicates.add(root.get("area").in(user.areaList()));
        }
        if (user.getBoundMeterId() != null) {
            predicates.add(cb.equal(root.get("meterId"), user.getBoundMeterId()));
        }
    }
}
