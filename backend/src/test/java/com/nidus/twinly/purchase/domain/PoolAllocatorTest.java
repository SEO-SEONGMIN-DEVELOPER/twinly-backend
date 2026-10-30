package com.nidus.twinly.purchase.domain;

import com.nidus.twinly.purchase.entity.PoolCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class PoolAllocatorTest {

    @Test
    @DisplayName("모든 유저가 한 번씩만 배치되고 풀마다 전체 성비에 가깝게 나뉜다")
    void order_keeps_overall_ratio_in_every_pool() {
        // given: 남 140, 여 125
        List<Long> males = LongStream.rangeClosed(1, 140).boxed().toList();
        List<Long> females = LongStream.rangeClosed(1001, 1125).boxed().toList();
        Set<Long> maleSet = Set.copyOf(males);

        // when
        List<Long> ordered = PoolAllocator.order(males, females, new Random(1));

        // then: 빠지거나 겹치는 유저가 없다
        assertThat(ordered).hasSize(265).doesNotHaveDuplicates();

        // and: 각 풀의 남자 수가 전체 성비 기준 값과 1명 넘게 차이 나지 않는다
        for (int start = 0; start < ordered.size(); start += PoolCounter.POOL_SIZE) {
            List<Long> pool = ordered.subList(start, Math.min(start + PoolCounter.POOL_SIZE, ordered.size()));
            long maleCount = pool.stream().filter(maleSet::contains).count();

            assertThat((double) maleCount).isCloseTo(pool.size() * 140.0 / 265, org.assertj.core.data.Offset.offset(1.0));
        }
    }

    @Test
    @DisplayName("한쪽 성별만 있어도 전원이 배치된다")
    void order_with_single_gender() {
        assertThat(PoolAllocator.order(List.of(1L, 2L, 3L), List.of(), new Random(1)))
                .containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(PoolAllocator.order(List.of(), List.of(4L, 5L), new Random(1)))
                .containsExactlyInAnyOrder(4L, 5L);
    }

    @Test
    @DisplayName("대상이 없으면 빈 목록을 반환한다")
    void order_with_no_users() {
        assertThat(PoolAllocator.order(List.of(), List.of(), new Random(1))).isEmpty();
    }

    @Test
    @DisplayName("시드에 따라 순서가 달라진다")
    void order_is_shuffled() {
        List<Long> males = LongStream.rangeClosed(1, 100).boxed().toList();

        assertThat(PoolAllocator.order(males, List.of(), new Random(1)))
                .isNotEqualTo(PoolAllocator.order(males, List.of(), new Random(2)));
    }
}
