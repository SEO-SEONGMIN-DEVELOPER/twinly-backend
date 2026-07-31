package com.nidus.twinly.device.repository;

import com.nidus.twinly.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByDeviceId(UUID deviceId);

    Optional<Device> findByUserIdAndDeviceId(Long userId, UUID deviceId);

    List<Device> findAllByUserIdAndPushToken(Long userId, String pushToken);
}
