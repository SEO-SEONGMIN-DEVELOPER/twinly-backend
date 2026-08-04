package com.nidus.twinly.device.entity;

import com.nidus.twinly.device.domain.DevicePlatform;
import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@DynamicUpdate
@Table(name = "devices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private UUID deviceId;

    @Enumerated(EnumType.STRING)
    private DevicePlatform platform;

    @Column(columnDefinition = "TEXT")
    private String pushToken;

    private Instant createdAt;

    public static Device create(Long userId, UUID deviceId, DevicePlatform platform, String pushToken) {
        Device device = new Device();

        device.userId = userId;
        device.deviceId = deviceId;
        device.platform = platform;
        device.pushToken = pushToken;
        device.createdAt = Instant.now();

        return device;
    }

    public void reregister(Long userId, DevicePlatform platform, String pushToken) {
        this.userId = userId;
        this.platform = platform;
        this.pushToken = pushToken;
    }

    public void revokeToken() {
        this.pushToken = null;
    }
}