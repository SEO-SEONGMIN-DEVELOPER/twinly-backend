package com.nidus.twinly.purchase.domain;

import com.nidus.twinly.common.domain.Gender;

import java.util.Collections;
import java.util.List;

public record PoolAssignment(
        int poolNumber,
        int memberCount,
        int maleCount,
        int femaleCount
) {

    public static PoolAssignment of(int poolNumber, List<Gender> genders) {
        return new PoolAssignment(poolNumber, genders.size(),
                Collections.frequency(genders, Gender.MALE), Collections.frequency(genders, Gender.FEMALE));
    }
}
