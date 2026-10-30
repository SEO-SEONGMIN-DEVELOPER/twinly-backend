package com.nidus.twinly.app.store;

import com.nidus.twinly.app.config.AppBlockProperties;
import com.nidus.twinly.common.logging.WarnLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppFeatureFlagStore {

    static final String DOT_WORLD_KEY = "app:feature:dot-world";

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final AppBlockProperties appBlockProperties;

    private final AtomicReference<CachedFlag> cache = new AtomicReference<>();

    public boolean dotWorldEnabled() {
        Instant now = clock.instant();
        CachedFlag cached = cache.get();
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.enabled();
        }

        boolean enabled;
        try {
            enabled = Boolean.parseBoolean(redisTemplate.opsForValue().get(DOT_WORLD_KEY));
        } catch (RuntimeException e) {
            enabled = cached != null && cached.enabled();
            WarnLog.log(log, "도트 세계 스위치를 Redis에서 읽지 못해 대체 값으로 동작합니다.", e, field("fallback", enabled));
        }

        cache.set(new CachedFlag(enabled, now.plus(appBlockProperties.cacheTtl())));
        return enabled;
    }

    public void saveDotWorldEnabled(boolean enabled) {
        redisTemplate.opsForValue().set(DOT_WORLD_KEY, Boolean.toString(enabled));
        cache.set(null);
    }

    private record CachedFlag(boolean enabled, Instant expiresAt) {
    }
}
