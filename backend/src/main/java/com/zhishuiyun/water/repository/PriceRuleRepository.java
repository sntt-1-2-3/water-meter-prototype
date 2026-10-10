package com.zhishuiyun.water.repository;

import com.zhishuiyun.water.entity.DomainEnums;
import com.zhishuiyun.water.entity.PriceRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceRuleRepository extends JpaRepository<PriceRule, Long> {

    List<PriceRule> findByMeterTypeOrderByTierAsc(DomainEnums.MeterType meterType);

    Optional<PriceRule> findFirstByMeterTypeAndTier(DomainEnums.MeterType meterType, Integer tier);
}
