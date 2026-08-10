package com.shipmate.repository;

import com.shipmate.model.HealthRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HealthRecordRepository extends JpaRepository<HealthRecord, Long> {
    List<HealthRecord> findTop20ByAppIdOrderByIdDesc(Long appId);
    List<HealthRecord> findTop100ByOrderByIdDesc();
}
