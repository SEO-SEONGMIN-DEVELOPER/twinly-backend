package com.nidus.twinly.purchase.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class PoolAllocator {

    private PoolAllocator() {
    }

    public static List<Long> order(List<Long> maleIds, List<Long> otherIds, Random random) {
        List<Long> males = shuffled(maleIds, random);
        List<Long> others = shuffled(otherIds, random);
        int total = males.size() + others.size();

        List<Long> ordered = new ArrayList<>(total);
        int maleIndex = 0;
        int otherIndex = 0;

        for (int position = 1; position <= total; position++) {
            if ((long) maleIndex * total < (long) position * males.size()) {
                ordered.add(males.get(maleIndex++));
            } else {
                ordered.add(others.get(otherIndex++));
            }
        }

        return ordered;
    }

    private static List<Long> shuffled(List<Long> ids, Random random) {
        List<Long> copied = new ArrayList<>(ids);
        Collections.shuffle(copied, random);

        return copied;
    }
}
