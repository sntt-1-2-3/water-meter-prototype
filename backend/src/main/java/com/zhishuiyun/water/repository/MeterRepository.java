package com.zhishuiyun.water.repository;

import com.zhishuiyun.water.entity.Meter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface MeterRepository extends JpaRepository<Meter, Long>, JpaSpecificationExecutor<Meter> {
    Optional<Meter> findByMeterId(String meterId);

    boolean existsByMeterId(String meterId);

    long countByType(com.zhishuiyun.water.entity.DomainEnums.MeterType type);
}
