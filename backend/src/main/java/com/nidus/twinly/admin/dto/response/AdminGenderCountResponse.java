package com.nidus.twinly.admin.dto.response;

import com.nidus.twinly.admin.dto.result.AdminGenderCountResult;

public record AdminGenderCountResponse(
        Long total,
        Long male,
        Long female
) {

    public static AdminGenderCountResponse from(AdminGenderCountResult result) {
        return new AdminGenderCountResponse(result.total(), result.male(), result.female());
    }
}
