package com.nidus.twinly.admin.dto.result;

import com.nidus.twinly.common.domain.Gender;

import java.util.Map;

public record AdminGenderCountResult(
        long total,
        long male,
        long female
) {

    public static AdminGenderCountResult from(Map<Gender, Long> countByGender) {
        long male = countByGender.getOrDefault(Gender.MALE, 0L);
        long female = countByGender.getOrDefault(Gender.FEMALE, 0L);
        long total = countByGender.values().stream().mapToLong(Long::longValue).sum();

        return new AdminGenderCountResult(total, male, female);
    }
}
