package com.nidus.twinly.app.store;

import com.nidus.twinly.app.config.AppBlockProperties;
import com.nidus.twinly.common.logging.WarnLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppFeatureFlagStore {

    static final String DOT_WORLD_KEY = "app:feature:dot-world";
    static final String COMPANY_SIGNUP_KEY = "app:feature:company-signup";

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final AppBlockProperties appBlockProperties;

    private final Map<String, CachedFlag> cache = new ConcurrentHashMap<>();

    public boolean dotWorldEnabled() {
        return enabled(DOT_WORLD_KEY, "도트 세계");
    }

    public void saveDotWorldEnabled(boolean enabled) {
        save(DOT_WORLD_KEY, enabled);
    }

    public boolean companySignupEnabled() {
        return enabled(COMPANY_SIGNUP_KEY, "직장인 가입");
    }

    public void saveCompanySignupEnabled(boolean enabled) {
        save(COMPANY_SIGNUP_KEY, enabled);
    }

    private boolean enabled(String key, String flagName) {
        Instant now = clock.instant();
        CachedFlag cached = cache.get(key);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.enabled();
        }

        boolean enabled;
        try {
            enabled = Boolean.parseBoolean(redisTemplate.opsForValue().get(key));
        } catch (RuntimeException e) {
            enabled = cached != null && cached.enabled();
            WarnLog.log(log, flagName + " 스위치를 Redis에서 읽지 못해 대체 값으로 동작합니다.", e, field("fallback", enabled));
        }

        cache.put(key, new CachedFlag(enabled, now.plus(appBlockProperties.cacheTtl())));
        return enabled;
    }

    private void save(String key, boolean enabled) {
        redisTemplate.opsForValue().set(key, Boolean.toString(enabled));
        cache.remove(key);
    }

    private record CachedFlag(boolean enabled, Instant expiresAt) {
    }
}
