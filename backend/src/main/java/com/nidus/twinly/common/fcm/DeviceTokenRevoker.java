package com.nidus.twinly.common.fcm;

import com.nidus.twinly.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DeviceTokenRevoker {

    private final DeviceRepository deviceRepository;

    @Transactional
    public void revoke(List<String> tokens) {
        if (tokens.isEmpty()) {
            return;
        }

        deviceRepository.revokeTokens(tokens);
    }
}
