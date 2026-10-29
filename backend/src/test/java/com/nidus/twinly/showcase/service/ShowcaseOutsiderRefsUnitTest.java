package com.nidus.twinly.showcase.service;

import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.crypto.CryptoProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class ShowcaseOutsiderRefsUnitTest {

    private final ShowcaseOutsiderRefs refs = new ShowcaseOutsiderRefs(new BlindIndexHasher(
            new CryptoProperties(Map.of("v1", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="), "v1", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")));

    @Test
    @DisplayName("같은 둘러보기·같은 사용자는 늘 같은 번호, 서로 다른 사용자는 겹치지 않고, 모두 2^40 이상이다")
    void refOf_is_stable_unique_and_large() {
        // given: 이미 나온 번호를 모아 둘 집합
        Set<Long> seen = new HashSet<>();

        // when & then: 사용자 5,000명 모두 2^40 이상·두 번 불러도 같음·서로 겹치지 않음
        LongStream.rangeClosed(1, 5_000).forEach(userId -> {
            long ref = refs.refOf(3312L, userId);
            assertThat(ref).isGreaterThanOrEqualTo(1L << 40).isEqualTo(refs.refOf(3312L, userId));
            assertThat(seen.add(ref)).isTrue();
        });
    }

    @Test
    @DisplayName("둘러보기가 다르면 같은 사용자도 다른 번호다")
    void refOf_differs_by_showcase() {
        // when & then: 같은 사용자라도 둘러보기 id 가 다르면 번호가 다르다
        assertThat(refs.refOf(3312L, 600L)).isNotEqualTo(refs.refOf(3313L, 600L));
    }
}
