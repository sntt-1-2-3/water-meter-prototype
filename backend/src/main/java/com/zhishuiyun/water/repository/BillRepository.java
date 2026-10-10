package com.zhishuiyun.water.repository;

import com.zhishuiyun.water.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, Long> {
    Optional<Bill> findByBatchNo(String batchNo);

    List<Bill> findAllByOrderByBatchNoDesc();
}
