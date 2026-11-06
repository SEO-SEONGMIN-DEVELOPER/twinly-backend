package com.nidus.twinly.app.store;

import com.nidus.twinly.app.config.AppBlockProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AppFeatureFlagStoreUnitTest {

    private static final Duration CACHE_TTL = Duration.ofSeconds(5);

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    MutableClock clock = new MutableClock(Instant.parse("2026-10-30T00:00:00Z"));
    AppFeatureFlagStore store;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new AppFeatureFlagStore(redisTemplate, clock, new AppBlockProperties(CACHE_TTL));
    }

    @Test
    @DisplayName("Redis에 값이 없으면 꺼짐으로 읽는다")
    void dotWorldEnabled_returnsFalseWhenKeyMissing() {
        // given: 스위치를 한 번도 켠 적 없는 상태
        given(valueOperations.get(AppFeatureFlagStore.DOT_WORLD_KEY)).willReturn(null);

        // when & then: 꺼짐으로 읽힌다
        assertThat(store.dotWorldEnabled()).isFalse();
    }

    @Test
    @DisplayName("Redis 값이 true면 켜짐으로 읽는다")
    void dotWorldEnabled_returnsTrueWhenKeyIsTrue() {
        // given: 켜 둔 상태
        given(valueOperations.get(AppFeatureFlagStore.DOT_WORLD_KEY)).willReturn("true");

        // when & then: 켜짐으로 읽힌다
        assertThat(store.dotWorldEnabled()).isTrue();
    }

    @Test
    @DisplayName("캐시 시간 안에는 Redis를 다시 읽지 않고, 지나면 새 값을 읽는다")
    void dotWorldEnabled_cachesUntilTtl() {
        // given: 처음에는 꺼짐, 그 뒤 다른 서버가 켠 상태
        given(valueOperations.get(AppFeatureFlagStore.DOT_WORLD_KEY)).willReturn("false", "true");

        // when: 캐시 시간 안에 두 번 읽고, 시간이 지난 뒤 다시 읽는다
        boolean first = store.dotWorldEnabled();
        clock.advance(CACHE_TTL.minusMillis(1));
        boolean withinTtl = store.dotWorldEnabled();
        clock.advance(Duration.ofMillis(1));
        boolean afterTtl = store.dotWorldEnabled();

        // then: 캐시 시간 안에는 옛 값, 지나면 새 값이고 Redis는 두 번만 읽는다
        assertThat(first).isFalse();
        assertThat(withinTtl).isFalse();
        assertThat(afterTtl).isTrue();
        then(valueOperations).should(times(2)).get(AppFeatureFlagStore.DOT_WORLD_KEY);
    }

    @Test
    @DisplayName("읽은 적 없이 Redis가 실패하면 꺼짐으로 동작한다")
    void dotWorldEnabled_returnsFalseWhenRedisFailsWithoutCache() {
        // given: Redis 장애
        given(valueOperations.get(AppFeatureFlagStore.DOT_WORLD_KEY)).willThrow(new RedisConnectionFailureException("down"));

        // when & then: 예외를 밖으로 던지지 않고 꺼짐으로 읽힌다
        assertThat(store.dotWorldEnabled()).isFalse();
    }

    @Test
    @DisplayName("켜져 있던 중 Redis가 실패하면 마지막 값을 유지한다")
    void dotWorldEnabled_keepsLastValueWhenRedisFails() {
        // given: 켜짐을 읽은 뒤 Redis 장애가 난 상태
        given(valueOperations.get(AppFeatureFlagStore.DOT_WORLD_KEY))
                .willReturn("true")
                .willThrow(new RedisConnectionFailureException("down"));
        store.dotWorldEnabled();
        clock.advance(CACHE_TTL);

        // when & then: 캐시가 지나도 마지막 값으로 읽힌다
        assertThat(store.dotWorldEnabled()).isTrue();
    }

    @Test
    @DisplayName("값을 저장하면 Redis에 쓰고 이 서버의 캐시를 비운다")
    void saveDotWorldEnabled_writesAndClearsCache() {
        // given: 꺼짐을 읽어 캐시에 담긴 상태
        given(valueOperations.get(AppFeatureFlagStore.DOT_WORLD_KEY)).willReturn("false", "true");
        store.dotWorldEnabled();

        // when: 캐시 시간 안에 켠다
        store.saveDotWorldEnabled(true);

        // then: Redis에 저장되고 다음 조회부터 바로 새 값을 읽는다
        then(valueOperations).should().set(AppFeatureFlagStore.DOT_WORLD_KEY, "true");
        assertThat(store.dotWorldEnabled()).isTrue();
    }

    @Test
    @DisplayName("직장인 가입 스위치는 Redis에 값이 없으면 꺼짐이고 true면 켜짐이다")
    void companySignupEnabled_readsOwnKey() {
        // given: 처음에는 값이 없고, 그 뒤 켜진 상태
        given(valueOperations.get(AppFeatureFlagStore.COMPANY_SIGNUP_KEY)).willReturn(null, "true");

        // when & then: 값이 없으면 꺼짐, 캐시가 지나 다시 읽으면 켜짐
        assertThat(store.companySignupEnabled()).isFalse();
        clock.advance(CACHE_TTL);
        assertThat(store.companySignupEnabled()).isTrue();
    }

    @Test
    @DisplayName("두 스위치는 캐시를 따로 쓰므로 한쪽을 읽거나 바꿔도 다른 쪽 값에 섞이지 않는다")
    void flags_are_cached_independently() {
        // given: 도트 세계는 켜짐, 직장인 가입은 꺼짐
        given(valueOperations.get(AppFeatureFlagStore.DOT_WORLD_KEY)).willReturn("true");
        given(valueOperations.get(AppFeatureFlagStore.COMPANY_SIGNUP_KEY)).willReturn("false", "true");

        // when: 둘 다 읽은 뒤 직장인 가입만 켠다
        boolean dotWorld = store.dotWorldEnabled();
        boolean companyBefore = store.companySignupEnabled();
        store.saveCompanySignupEnabled(true);

        // then: 각자 값이 유지되고, 직장인 가입만 바로 새 값을 읽으며 도트 세계는 캐시를 그대로 쓴다
        assertThat(dotWorld).isTrue();
        assertThat(companyBefore).isFalse();
        assertThat(store.companySignupEnabled()).isTrue();
        assertThat(store.dotWorldEnabled()).isTrue();
        then(valueOperations).should().set(AppFeatureFlagStore.COMPANY_SIGNUP_KEY, "true");
        then(valueOperations).should(times(1)).get(AppFeatureFlagStore.DOT_WORLD_KEY);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
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
            return instant;
        }
    }
}
