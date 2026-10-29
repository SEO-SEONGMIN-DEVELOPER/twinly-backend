package com.nidus.twinly.showcase.service;

import com.nidus.twinly.common.crypto.BlindIndexHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
@RequiredArgsConstructor
public class ShowcaseOutsiderRefs {

    private static final int HALF_BITS = 20;
    private static final long HALF_MASK = (1L << HALF_BITS) - 1;
    private static final long DOMAIN = 1L << (HALF_BITS * 2);
    private static final int ROUNDS = 4;
    private static final String CONTEXT = "showcase-outsider-ref";

    private final BlindIndexHasher blindIndexHasher;

    public long refOf(long showcaseId, long userId) {
        if (userId < 0 || userId >= DOMAIN) {
            throw new IllegalArgumentException("둘러보기 번호로 바꿀 수 없는 사용자 ID 입니다: " + userId);
        }

        long left = userId >>> HALF_BITS;
        long right = userId & HALF_MASK;
        for (int round = 0; round < ROUNDS; round++) {
            long mixed = left ^ roundValue(showcaseId, round, right);
            left = right;
            right = mixed;
        }

        return DOMAIN + ((left << HALF_BITS) | right);
    }

    private long roundValue(long showcaseId, int round, long half) {
        byte[] digest = Base64.getDecoder().decode(blindIndexHasher.hash(CONTEXT + ":" + showcaseId + ":" + round + ":" + half));
        long value = ((digest[0] & 0xFFL) << 16) | ((digest[1] & 0xFFL) << 8) | (digest[2] & 0xFFL);
        return value & HALF_MASK;
    }
}
