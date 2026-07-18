package com.nidus.twinly.notification.repository;

import com.nidus.twinly.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query(value = """
        SELECT COUNT(*)
        FROM notifications 
        WHERE user_id = :userId 
            AND read_at IS NULL
        """, nativeQuery = true)
    int countUnreadByUserId(@Param("userId") Long userId);
}