package com.nidus.twinly.device.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private UUID deviceId;

    private String deviceModel;

    private String pushToken;

    private Instant createdAt;

    public static Device create(Long userId, UUID deviceId, String deviceModel, String pushToken) {
        Device device = new Device();

        device.userId = userId;
        device.deviceId = deviceId;
        device.deviceModel = deviceModel;
        device.pushToken = pushToken;
        device.createdAt = Instant.now();

        return device;
    }

    public void reregister(Long userId, String deviceModel, String pushToken) {
        this.userId = userId;
        this.deviceModel = deviceModel;
        this.pushToken = pushToken;
    }

    public void revokeToken() {
        this.pushToken = null;
    }
}