package com.zhishuiyun.water.repository;

import com.zhishuiyun.water.entity.BillItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillItemRepository extends JpaRepository<BillItem, Long> {
    List<BillItem> findByBatchNo(String batchNo);

    List<BillItem> findByMeterIdOrderByIdDesc(String meterId);

    Optional<BillItem> findFirstByMeterIdAndPeriod(String meterId, String period);
}
