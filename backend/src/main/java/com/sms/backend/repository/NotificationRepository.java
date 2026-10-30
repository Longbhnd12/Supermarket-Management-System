package com.sms.backend.repository;

import com.sms.backend.entity.Notification;
import com.sms.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Query("SELECT n FROM Notification n WHERE n.user = :user OR n.targetRole = :role")
    Page<Notification> findForRecipient(@Param("user") User user, @Param("role") String role, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE (n.user = :user OR n.targetRole = :role) AND n.isRead = :isRead")
    Page<Notification> findForRecipientAndRead(@Param("user") User user, @Param("role") String role, @Param("isRead") boolean isRead, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE (n.user = :user OR n.targetRole = :role) AND n.isRead = false")
    long countUnreadForRecipient(@Param("user") User user, @Param("role") String role);
}
