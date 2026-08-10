package com.shipmate.repository;

import com.shipmate.model.DeploymentRun;
import com.shipmate.model.RunState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeploymentRunRepository extends JpaRepository<DeploymentRun, Long> {

    List<DeploymentRun> findByAppIdOrderByIdDesc(Long appId);

    List<DeploymentRun> findByStateOrderByIdAsc(RunState state);

    @Modifying
    @Query("update DeploymentRun r set r.state = :newState where r.id = :id and r.state = :expectedState")
    int claim(@Param("id") Long id, @Param("newState") RunState newState, @Param("expectedState") RunState expectedState);

    Optional<DeploymentRun> findTopByAppIdAndStateOrderByIdDesc(Long appId, RunState state);

    List<DeploymentRun> findTop20ByOrderByIdDesc();
}
