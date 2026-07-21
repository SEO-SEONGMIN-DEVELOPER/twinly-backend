package com.nidus.twinly.notification.repository;

import com.nidus.twinly.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
