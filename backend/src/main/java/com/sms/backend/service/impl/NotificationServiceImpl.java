package com.sms.backend.service.impl;

import com.sms.backend.dto.response.NotificationResponse;
import com.sms.backend.dto.response.PageResponse;
import com.sms.backend.entity.Notification;
import com.sms.backend.entity.User;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.exception.UnauthorizedException;
import com.sms.backend.repository.NotificationRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {
    private static final int MAX_PAGE_SIZE=100;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override public PageResponse<NotificationResponse> getNotifications(String status, int page, int size) {
        User currentUser=currentUser(); String role=role(currentUser); String normalized=normalizeStatus(status);
        Pageable pageable=PageRequest.of(Math.max(page,0),Math.min(Math.max(size,1),MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC,"createdAt"));
        Page<Notification> result="ALL".equals(normalized)?notificationRepository.findForRecipient(currentUser,role,pageable):notificationRepository.findForRecipientAndRead(currentUser,role,"READ".equals(normalized),pageable);
        return PageResponse.from(result.map(this::toResponse));
    }
    @Override public long getUnreadCount() { User currentUser=currentUser(); return notificationRepository.countUnreadForRecipient(currentUser,role(currentUser)); }
    @Override @Transactional public NotificationResponse markAsRead(Long notificationId) {
        User currentUser=currentUser(); Notification notification=notificationRepository.findById(notificationId).orElseThrow(()->new ResourceNotFoundException("Notification not found: "+notificationId));
        if (!belongsTo(notification,currentUser)) throw new ResourceNotFoundException("Notification not found: "+notificationId);
        notification.setIsRead(true); return toResponse(notificationRepository.save(notification));
    }
    private User currentUser() { Authentication auth=SecurityContextHolder.getContext().getAuthentication(); if(auth==null||!auth.isAuthenticated()||"anonymousUser".equals(auth.getName())) throw new UnauthorizedException("Authentication is required to access notifications"); return userRepository.findByUsername(auth.getName()).orElseThrow(()->new UnauthorizedException("Authenticated user was not found")); }
    private String role(User user) { return user.getRole()==null||user.getRole().getRoleName()==null?"":user.getRole().getRoleName().name(); }
    private boolean belongsTo(Notification notification,User user) { return (notification.getUser()!=null&&notification.getUser().getUserId().equals(user.getUserId()))||role(user).equals(notification.getTargetRole()); }
    private String normalizeStatus(String status) { String value=status==null||status.isBlank()?"ALL":status.trim().toUpperCase(Locale.ROOT); if(!value.equals("ALL")&&!value.equals("READ")&&!value.equals("UNREAD"))throw new BadRequestException("Unsupported notification status: "+status);return value; }
    private NotificationResponse toResponse(Notification notification) { return new NotificationResponse(notification.getNotificationId(),notification.getType(),notification.getTitle(),notification.getMessage(),Boolean.TRUE.equals(notification.getIsRead()),notification.getCreatedAt()); }
}
