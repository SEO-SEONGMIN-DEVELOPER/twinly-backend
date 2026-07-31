package com.nidus.twinly.push.service;

import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.device.repository.DeviceRepository;
import com.nidus.twinly.push.dto.command.PushTokenRegisterCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PushService {

    private final DeviceRepository deviceRepository;

    @Transactional
    public void register(Long userId, PushTokenRegisterCommand command) {
        deviceRepository.upsert(userId, command.deviceId(), command.deviceModel(), command.fcmToken());

        deviceRepository.findAllByUserIdAndPushToken(userId, command.fcmToken()).stream()
                .filter(device -> !device.getDeviceId().equals(command.deviceId()))
                .forEach(Device::revokeToken);
    }

    @Transactional
    public void revoke(Long userId, UUID deviceId) {
        deviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .ifPresent(Device::revokeToken);
    }
}
