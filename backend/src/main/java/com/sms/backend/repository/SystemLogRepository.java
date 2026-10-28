package com.sms.backend.repository;

import com.sms.backend.entity.SystemLog;
import com.sms.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {

    List<SystemLog> findByUser(User user);

    List<SystemLog> findByAction(String action);

    List<SystemLog> findByEntityTypeAndEntityId(String entityType, Long entityId);
}
