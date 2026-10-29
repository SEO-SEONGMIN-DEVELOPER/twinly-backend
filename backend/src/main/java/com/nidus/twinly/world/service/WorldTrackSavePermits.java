package com.nidus.twinly.world.service;

import com.nidus.twinly.world.config.WorldTrackProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

@Component
public class WorldTrackSavePermits {

    private final Semaphore semaphore;

    public WorldTrackSavePermits(WorldTrackProperties worldTrackProperties) {
        this.semaphore = new Semaphore(worldTrackProperties.maxConcurrentSaves());
    }

    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    public void release() {
        semaphore.release();
    }
}
