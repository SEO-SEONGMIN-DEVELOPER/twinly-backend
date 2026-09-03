package com.nidus.twinly.app.store;

import com.nidus.twinly.app.domain.AppBlockPolicy;
import com.nidus.twinly.app.domain.AppPlatform;
import com.nidus.twinly.app.domain.AppVersionPolicy;
import com.nidus.twinly.app.domain.MaintenanceState;
import com.nidus.twinly.common.jackson.EnumJsonNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppBlockPolicyStore {

    static final String MAINTENANCE_KEY = "app:maintenance";
    static final String VERSION_POLICY_KEY_PREFIX = "app:version-policy:";
    static final Duration CACHE_TTL = Duration.ofSeconds(5);

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final AtomicReference<CachedSnapshot> cache = new AtomicReference<>();

    public AppBlockPolicy current() {
        Instant now = clock.instant();
        CachedSnapshot snapshot = cache.get();

        if (snapshot != null && now.isBefore(snapshot.expiresAt())) {
            return snapshot.policy();
        }

        AppBlockPolicy policy;
        try {
            policy = load();
        } catch (RuntimeException e) {
            policy = snapshot != null ? snapshot.policy() : AppBlockPolicy.none();
            log.warn("앱 차단 정책을 Redis에서 읽지 못해 {}으로 동작합니다.", snapshot != null ? "마지막 값" : "차단 없음", e);
        }

        cache.set(new CachedSnapshot(policy, now.plus(CACHE_TTL)));
        return policy;
    }

    public void saveMaintenance(MaintenanceState state) {
        redisTemplate.opsForValue().set(MAINTENANCE_KEY, jsonMapper.writeValueAsString(state));
        cache.set(null);
    }

    public void saveVersionPolicy(AppPlatform platform, AppVersionPolicy policy) {
        redisTemplate.opsForValue().set(versionPolicyKey(platform), jsonMapper.writeValueAsString(policy));
        cache.set(null);
    }

    private AppBlockPolicy load() {
        String maintenanceJson = redisTemplate.opsForValue().get(MAINTENANCE_KEY);
        MaintenanceState maintenance = maintenanceJson == null
                ? MaintenanceState.none()
                : jsonMapper.readValue(maintenanceJson, MaintenanceState.class);

        Map<AppPlatform, AppVersionPolicy> versionPolicies = new EnumMap<>(AppPlatform.class);
        for (AppPlatform platform : AppPlatform.values()) {
            String policyJson = redisTemplate.opsForValue().get(versionPolicyKey(platform));
            if (policyJson != null) {
                versionPolicies.put(platform, jsonMapper.readValue(policyJson, AppVersionPolicy.class));
            }
        }

        return new AppBlockPolicy(maintenance, Map.copyOf(versionPolicies));
    }

    static String versionPolicyKey(AppPlatform platform) {
        return VERSION_POLICY_KEY_PREFIX + EnumJsonNames.of(platform);
    }

    private record CachedSnapshot(AppBlockPolicy policy, Instant expiresAt) {
    }
}
