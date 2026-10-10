package com.zhishuiyun.water.repository;

import com.zhishuiyun.water.entity.DomainEnums;
import com.zhishuiyun.water.entity.ReadingTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReadingTaskRepository extends JpaRepository<ReadingTask, Long> {

    List<ReadingTask> findAllByOrderByIdDesc();

    Optional<ReadingTask> findFirstByPeriodAndStatusNot(String period, DomainEnums.TaskStatus status);
}
