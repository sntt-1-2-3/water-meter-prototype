package com.zhishuiyun.water.repository;

import com.zhishuiyun.water.entity.Reading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReadingRepository extends JpaRepository<Reading, Long> {

    Optional<Reading> findFirstByMeterIdOrderByTsDesc(String meterId);

    List<Reading> findByMeterIdAndTsBetweenOrderByTsAsc(String meterId, LocalDateTime from, LocalDateTime to);

    boolean existsByMeterIdAndTs(String meterId, LocalDateTime ts);

    long countByTsBetween(LocalDateTime from, LocalDateTime to);

    List<Reading> findByTsBetween(LocalDateTime from, LocalDateTime to);
}
