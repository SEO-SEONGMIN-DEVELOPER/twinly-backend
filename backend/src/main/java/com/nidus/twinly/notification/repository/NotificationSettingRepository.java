package com.nidus.twinly.notification.repository;

import com.nidus.twinly.notification.domain.NotificationChannel;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.notification.entity.NotificationSetting;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    List<NotificationSetting> findAllByUserIdAndChannel(Long userId, NotificationChannel channel);

    Optional<NotificationSetting> findByUserIdAndChannelAndType(Long userId, NotificationChannel channel, NotificationType type);

    @Modifying
    @Query(value = """
            INSERT INTO notification_settings (user_id, channel, type, enabled, created_at)
            VALUES (:userId, :channel, :type, :enabled, UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE enabled = :enabled
            """, nativeQuery = true)
    void upsertEnabled(@Param("userId") Long userId, @Param("channel") String channel,
                       @Param("type") String type, @Param("enabled") boolean enabled);
}
