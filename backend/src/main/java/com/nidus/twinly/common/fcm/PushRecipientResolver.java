package com.nidus.twinly.common.fcm;

import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.device.repository.DeviceRepository;
import com.nidus.twinly.notification.domain.NotificationChannel;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.notification.entity.NotificationSetting;
import com.nidus.twinly.notification.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PushRecipientResolver {

    private final DeviceRepository deviceRepository;
    private final NotificationSettingRepository notificationSettingRepository;

    public List<Device> resolve(List<Long> userIds, NotificationType type) {
        if (userIds.isEmpty()) {
            return List.of();
        }

        Set<Long> disabledUserIds = notificationSettingRepository
                .findAllByUserIdInAndChannelAndType(userIds, NotificationChannel.PUSH, type).stream()
                .filter(setting -> !setting.getEnabled())
                .map(NotificationSetting::getUserId)
                .collect(Collectors.toSet());

        List<Long> allowedUserIds = userIds.stream()
                .filter(userId -> !disabledUserIds.contains(userId))
                .toList();

        if (allowedUserIds.isEmpty()) {
            return List.of();
        }

        return deviceRepository.findAllByUserIdInAndPushTokenIsNotNull(allowedUserIds);
    }
}
