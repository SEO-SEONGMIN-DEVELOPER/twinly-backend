package com.nidus.twinly.device.repository;

import com.nidus.twinly.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByDeviceId(UUID deviceId);

    Optional<Device> findByUserIdAndDeviceId(Long userId, UUID deviceId);

    List<Device> findAllByUserIdAndPushToken(Long userId, String pushToken);

    @Modifying
    @Query(value = """
            INSERT INTO devices (user_id, device_id, device_model, push_token, created_at)
            VALUES (:userId, :deviceId, :deviceModel, :pushToken, UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE user_id = :userId, device_model = :deviceModel, push_token = :pushToken
            """, nativeQuery = true)
    void upsert(@Param("userId") Long userId, @Param("deviceId") UUID deviceId,
                @Param("deviceModel") String deviceModel, @Param("pushToken") String pushToken);
}
