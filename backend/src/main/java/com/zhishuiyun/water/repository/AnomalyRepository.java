package com.zhishuiyun.water.repository;

import com.zhishuiyun.water.entity.Anomaly;
import com.zhishuiyun.water.entity.DomainEnums;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnomalyRepository extends JpaRepository<Anomaly, Long> {
    Optional<Anomaly> findByAnomalyId(String anomalyId);
    List<Anomaly> findByMeterIdAndStatusNot(String meterId, DomainEnums.AnomalyStatus status);
    List<Anomaly> findAllByOrderByIdDesc();

    List<Anomaly> findByLevel(DomainEnums.RiskLevel level);

    List<Anomaly> findByStatus(DomainEnums.AnomalyStatus status);

    long countByStatus(DomainEnums.AnomalyStatus status);

    long countByLevel(DomainEnums.RiskLevel level);
}
