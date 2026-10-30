package com.nidus.twinly.app.service;

import com.nidus.twinly.app.dto.command.DotWorldFlagUpdateCommand;
import com.nidus.twinly.app.store.AppFeatureFlagStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppFeatureFlagService {

    private final AppFeatureFlagStore appFeatureFlagStore;

    public boolean dotWorldEnabled() {
        return appFeatureFlagStore.dotWorldEnabled();
    }

    public void updateDotWorld(DotWorldFlagUpdateCommand command) {
        appFeatureFlagStore.saveDotWorldEnabled(command.enabled());
    }
}
