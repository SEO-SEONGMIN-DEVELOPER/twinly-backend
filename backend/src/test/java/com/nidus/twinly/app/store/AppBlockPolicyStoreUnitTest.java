package com.nidus.twinly.app.store;

import com.nidus.twinly.app.domain.AppBlockPolicy;
import com.nidus.twinly.app.domain.AppPlatform;
import com.nidus.twinly.app.domain.AppVersion;
import com.nidus.twinly.app.domain.AppVersionPolicy;
import com.nidus.twinly.app.domain.MaintenanceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AppBlockPolicyStoreUnitTest {

    private static final String IOS_KEY = AppBlockPolicyStore.VERSION_POLICY_KEY_PREFIX + "ios";
    private static final String ANDROID_KEY = AppBlockPolicyStore.VERSION_POLICY_KEY_PREFIX + "android";

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    MutableClock clock = new MutableClock(Instant.parse("2026-09-03T00:00:00Z"));
    AppBlockPolicyStore store;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        store = new AppBlockPolicyStore(redisTemplate, JsonMapper.builder().build(), clock);
    }

    @Test
    @DisplayName("Redis에 아무 키도 없으면 점검 아님·버전 제한 없음으로 읽는다")
    void current_returnsNoneWhenKeysMissing() {
        // given
        given(valueOperations.get(anyString())).willReturn(null);

        // when
        AppBlockPolicy policy = store.current();

        // then
        assertThat(policy.maintenance()).isEqualTo(MaintenanceState.none());
        assertThat(policy.versionPolicies()).isEmpty();
    }

    @Test
    @DisplayName("점검 상태와 플랫폼별 버전 정책을 Redis JSON에서 읽는다")
    void current_loadsMaintenanceAndVersionPolicies() {
        // given
        given(valueOperations.get(AppBlockPolicyStore.MAINTENANCE_KEY))
                .willReturn("{\"active\":true,\"message\":\"점검 중이에요.\",\"until\":\"2026-09-03T09:00:00Z\"}");
        given(valueOperations.get(IOS_KEY))
                .willReturn("{\"minVersion\":\"0.2.0\",\"storeUrl\":\"https://apps.apple.com/kr/app/id1\"}");
        given(valueOperations.get(ANDROID_KEY)).willReturn(null);

        // when
        AppBlockPolicy policy = store.current();

        // then
        assertThat(policy.maintenance())
                .isEqualTo(new MaintenanceState(true, "점검 중이에요.", Instant.parse("2026-09-03T09:00:00Z")));
        assertThat(policy.versionPolicyOf(AppPlatform.IOS))
                .contains(new AppVersionPolicy(new AppVersion(0, 2, 0), "https://apps.apple.com/kr/app/id1"));
        assertThat(policy.versionPolicyOf(AppPlatform.ANDROID)).isEmpty();
    }

    @Test
    @DisplayName("TTL 안에서는 Redis를 다시 읽지 않고 캐시된 값을 돌려준다")
    void current_usesCacheWithinTtl() {
        // given
        given(valueOperations.get(anyString())).willReturn(null);
        store.current();

        // when: TTL 직전까지 시간이 흐른 뒤 다시 조회
        clock.advance(AppBlockPolicyStore.CACHE_TTL.minusMillis(1));
        store.current();

        // then: 점검 1회 + 플랫폼 2회 = 최초 로드의 3회뿐
        then(valueOperations).should(times(3)).get(anyString());
    }

    @Test
    @DisplayName("TTL이 지나면 Redis를 다시 읽는다")
    void current_reloadsAfterTtl() {
        // given
        given(valueOperations.get(anyString())).willReturn(null);
        store.current();

        // when
        clock.advance(AppBlockPolicyStore.CACHE_TTL);
        store.current();

        // then
        then(valueOperations).should(times(6)).get(anyString());
    }

    @Test
    @DisplayName("캐시가 없는 상태에서 Redis가 실패하면 차단 없음으로 fail-open 한다")
    void current_failsOpenWhenNoCacheAndRedisDown() {
        // given
        given(valueOperations.get(anyString())).willThrow(new RedisConnectionFailureException("down"));

        // when
        AppBlockPolicy policy = store.current();

        // then
        assertThat(policy).isEqualTo(AppBlockPolicy.none());
    }

    @Test
    @DisplayName("캐시가 만료된 뒤 Redis가 실패하면 마지막 값을 유지해 점검 중 잠금이 풀리지 않게 한다")
    void current_keepsLastValueWhenRedisDownAfterExpiry() {
        // given: 점검 중 상태를 한 번 읽어 둔다
        given(valueOperations.get(AppBlockPolicyStore.MAINTENANCE_KEY))
                .willReturn("{\"active\":true,\"message\":null,\"until\":null}")
                .willThrow(new RedisConnectionFailureException("down"));
        given(valueOperations.get(IOS_KEY)).willReturn(null);
        given(valueOperations.get(ANDROID_KEY)).willReturn(null);
        store.current();

        // when: 만료 후 Redis 장애
        clock.advance(AppBlockPolicyStore.CACHE_TTL);
        AppBlockPolicy policy = store.current();

        // then
        assertThat(policy.maintenance().active()).isTrue();
    }

    @Test
    @DisplayName("Redis 실패 뒤에도 TTL 동안은 재시도하지 않아 장애 중 Redis를 두들기지 않는다")
    void current_doesNotRetryWithinTtlAfterFailure() {
        // given
        given(valueOperations.get(anyString())).willThrow(new RedisConnectionFailureException("down"));
        store.current();

        // when
        clock.advance(Duration.ofSeconds(1));
        store.current();

        // then: 첫 시도의 1회(첫 GET에서 예외)뿐
        then(valueOperations).should(times(1)).get(anyString());
    }

    @Test
    @DisplayName("점검 상태를 저장하면 JSON으로 쓰고 캐시를 비워 같은 서버에서 즉시 반영된다")
    void saveMaintenance_writesJsonAndInvalidatesCache() {
        // given: 점검 아님을 캐시해 둔 상태
        given(valueOperations.get(anyString())).willReturn(null);
        store.current();

        // when
        store.saveMaintenance(new MaintenanceState(true, "점검 중이에요.", Instant.parse("2026-09-03T09:00:00Z")));
        given(valueOperations.get(AppBlockPolicyStore.MAINTENANCE_KEY))
                .willReturn("{\"active\":true,\"message\":\"점검 중이에요.\",\"until\":\"2026-09-03T09:00:00Z\"}");
        AppBlockPolicy policy = store.current();

        // then
        then(valueOperations).should().set(AppBlockPolicyStore.MAINTENANCE_KEY,
                "{\"active\":true,\"message\":\"점검 중이에요.\",\"until\":\"2026-09-03T09:00:00Z\"}");
        assertThat(policy.maintenance().active()).isTrue();
    }

    @Test
    @DisplayName("버전 정책은 플랫폼별 키에 minVersion을 문자열로 저장한다")
    void saveVersionPolicy_writesPlatformKey() {
        // when
        store.saveVersionPolicy(AppPlatform.ANDROID,
                new AppVersionPolicy(new AppVersion(1, 2, 3), "https://play.google.com/store/apps/details?id=com.nidus.twinly"));

        // then
        then(valueOperations).should().set(ANDROID_KEY,
                "{\"minVersion\":\"1.2.3\",\"storeUrl\":\"https://play.google.com/store/apps/details?id=com.nidus.twinly\"}");
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
