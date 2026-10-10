package com.zhishuiyun.water.repository;

import com.zhishuiyun.water.entity.Anomaly;
import com.zhishuiyun.water.entity.DomainEnums;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnomalyRepository extends JpaRepository<Anomaly, Long> {
    List<Anomaly> findAllByOrderByIdDesc();

    List<Anomaly> findByLevel(DomainEnums.RiskLevel level);

    List<Anomaly> findByStatus(DomainEnums.AnomalyStatus status);

    long countByStatus(DomainEnums.AnomalyStatus status);

    long countByLevel(DomainEnums.RiskLevel level);
}
