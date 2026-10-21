package com.nidus.twinly.admin.dto.result;

import java.time.Instant;
import java.time.LocalDate;
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
        long unknownBirthYearMembers,
        List<Daily> daily,
        EarlyGrantSlots earlyGrantSlots
) {

    public record EarlyGrantSlots(
            int limitPerGender,
            long maleRemaining,
            long femaleRemaining,
            Instant endsAt
    ) {
    }

    public record Daily(
            LocalDate date,
            AdminGenderCountResult signups,
            AdminGenderCountResult subscriptions,
            AdminGenderCountResult withdrawals,
            AdminGenderCountResult withdrawalRequests
    ) {
    }

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
