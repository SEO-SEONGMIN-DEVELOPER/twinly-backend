package com.nidus.twinly.admin.dto.response;

import com.nidus.twinly.admin.dto.result.AdminDashboardResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AdminDashboardResponse(
        @Schema(description = "가입자. 시드·탈퇴 신청·탈퇴 회원 제외")
        AdminGenderCountResponse members,
        @Schema(description = "시즌 참가자. 현재 시즌에 참가한 가입자. paid·trial·earlyGrant 는 그중 이용권 종류별 수이고, 이용권이 만료된 참가자는 어느 종류에도 들지 않는다")
        Subscribers subscribers,
        @Schema(description = "탈퇴자. 탈퇴 완료 + 탈퇴 유예, 시드 제외")
        Withdrawn withdrawn,
        @Schema(description = "가입자가 있는 학교. 가입자 많은 순")
        List<Organization> organizations,
        @Schema(description = "가입자의 출생연도별 가입자·시즌 참가자(현재 시즌) 수. 최근 연도부터")
        List<BirthYear> birthYears,
        @Schema(description = "생년월일을 읽을 수 없어 출생연도 집계에서 빠진 가입자 수")
        Long unknownBirthYearMembers,
        @Schema(description = "최근 30일(KST, 오늘 포함) 날짜별로 그날 일어난 가입·시즌 참가·탈퇴 완료·탈퇴 신청(아직 유예 중) 수. 시드 제외, 오래된 날부터")
        List<Daily> daily,
        EarlyGrantSlots earlyGrantSlots
) {

    public static AdminDashboardResponse from(AdminDashboardResult result) {
        return new AdminDashboardResponse(
                AdminGenderCountResponse.from(result.members()),
                new Subscribers(AdminGenderCountResponse.from(result.subscribers()),
                        result.paidSubscribers(), result.trialSubscribers(), result.earlyGrantSubscribers()),
                new Withdrawn(AdminGenderCountResponse.from(result.withdrawn()),
                        result.withdrawalCompleted(), result.withdrawalPending()),
                result.organizations().stream()
                        .map(organization -> new Organization(organization.name(), organization.memberCount()))
                        .toList(),
                result.birthYears().stream()
                        .map(birthYear -> new BirthYear(birthYear.year(), birthYear.members(), birthYear.subscribers()))
                        .toList(),
                result.unknownBirthYearMembers(),
                result.daily().stream()
                        .map(day -> new Daily(day.date(),
                                AdminGenderCountResponse.from(day.signups()),
                                AdminGenderCountResponse.from(day.subscriptions()),
                                AdminGenderCountResponse.from(day.withdrawals()),
                                AdminGenderCountResponse.from(day.withdrawalRequests())))
                        .toList(),
                new EarlyGrantSlots(
                        result.earlyGrantSlots().limitPerGender(),
                        result.earlyGrantSlots().maleRemaining(),
                        result.earlyGrantSlots().femaleRemaining(),
                        result.earlyGrantSlots().endsAt())
        );
    }

    public record Subscribers(
            AdminGenderCountResponse count,
            Long paid,
            Long trial,
            Long earlyGrant
    ) {
    }

    public record Withdrawn(
            AdminGenderCountResponse count,
            Long completed,
            Long pending
    ) {
    }

    public record Organization(
            String name,
            Long memberCount
    ) {
    }

    @Schema(description = "선착순 무료 혜택 남은 자리. 성별마다 한도에서 이미 배정된 수를 뺀 값이고, 배정된 회원이 탈퇴해도 자리는 돌아오지 않는다. endsAt 이후 가입에는 배정하지 않는다")
    public record EarlyGrantSlots(
            Integer limitPerGender,
            Long maleRemaining,
            Long femaleRemaining,
            Instant endsAt
    ) {
    }

    public record Daily(
            LocalDate date,
            @Schema(description = "그날 가입한 회원 전체. 이후 탈퇴한 회원도 포함")
            AdminGenderCountResponse signups,
            @Schema(description = "그날 시즌에 참가한 회원 전체. 가입일과 관계없다")
            AdminGenderCountResponse subscriptions,
            @Schema(description = "그날 탈퇴가 완료된 회원")
            AdminGenderCountResponse withdrawals,
            @Schema(description = "그날 탈퇴를 신청해 아직 유예 중인 회원")
            AdminGenderCountResponse withdrawalRequests
    ) {
    }

    public record BirthYear(
            Integer year,
            Long members,
            Long subscribers
    ) {
    }
}
