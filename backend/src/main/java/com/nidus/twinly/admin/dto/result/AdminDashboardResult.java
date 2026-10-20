package com.nidus.twinly.admin.dto.result;

import java.util.List;

public record AdminDashboardResult(
        AdminGenderCountResult members,
        AdminGenderCountResult subscribers,
        long paidSubscribers,
        long trialSubscribers,
        long earlyGrantSubscribers,
        AdminGenderCountResult withdrawn,
        long withdrawalCompleted,
        long withdrawalPending,
        List<Organization> organizations,
        List<BirthYear> birthYears,
        long unknownBirthYearMembers
) {

    public record BirthYear(
            int year,
            long members,
            long subscribers
    ) {
    }

    public record Organization(
            String name,
            long memberCount
    ) {
    }
}
