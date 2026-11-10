package com.zhishuiyun.water.repository;

import com.zhishuiyun.water.entity.PhotoReading;
import com.zhishuiyun.water.entity.PhotoReadingSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Collection;

public interface PhotoReadingRepository extends JpaRepository<PhotoReading, Long>, JpaSpecificationExecutor<PhotoReading> {
    Page<PhotoReadingSnapshot> findByMeterIdIn(Collection<String> meterIds, Pageable pageable);
}
