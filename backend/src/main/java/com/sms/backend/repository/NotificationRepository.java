package com.sms.backend.repository;

import com.sms.backend.entity.Notification;
import com.sms.backend.entity.User;
import com.sms.backend.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUser(User user);

    List<Notification> findByUserAndIsRead(User user, Boolean isRead);

    List<Notification> findByTargetRole(String targetRole);

    List<Notification> findByType(NotificationType type);
}
