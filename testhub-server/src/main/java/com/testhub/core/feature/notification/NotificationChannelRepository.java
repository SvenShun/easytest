package com.testhub.core.feature.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, String> {
    List<NotificationChannel> findAllByOrderByCreatedAtDesc();
    List<NotificationChannel> findByEnabledTrue();
}
