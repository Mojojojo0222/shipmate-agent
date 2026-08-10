package com.shipmate.repository;

import com.shipmate.model.App;
import com.shipmate.model.AppStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppRepository extends JpaRepository<App, Long> {
    Optional<App> findByRepoOwnerAndRepoName(String repoOwner, String repoName);
    List<App> findByStatusNot(AppStatus status);
}
