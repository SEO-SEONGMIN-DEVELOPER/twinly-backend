package com.nidus.twinly.notification.repository;

import com.nidus.twinly.notification.domain.NotificationChannel;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.notification.entity.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    List<NotificationSetting> findAllByUserIdAndChannel(Long userId, NotificationChannel channel);

    Optional<NotificationSetting> findByUserIdAndChannelAndType(Long userId, NotificationChannel channel, NotificationType type);
}
