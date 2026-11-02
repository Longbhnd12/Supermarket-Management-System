package com.sms.backend.repository;

import com.sms.backend.entity.SystemLog;
import com.sms.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, Long>, JpaSpecificationExecutor<SystemLog> {

    List<SystemLog> findByUser(User user);

    List<SystemLog> findByAction(String action);

    List<SystemLog> findByEntityTypeAndEntityId(String entityType, Long entityId);

    @Query("SELECT DISTINCT s.action FROM SystemLog s WHERE s.action IS NOT NULL ORDER BY s.action")
    List<String> findDistinctActions();

    @Query("SELECT DISTINCT s.entityType FROM SystemLog s WHERE s.entityType IS NOT NULL ORDER BY s.entityType")
    List<String> findDistinctEntityTypes();
}
