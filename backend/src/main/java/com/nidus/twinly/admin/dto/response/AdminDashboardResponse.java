package com.nidus.twinly.admin.dto.response;

import com.nidus.twinly.admin.dto.result.AdminDashboardResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record AdminDashboardResponse(
        @Schema(description = "가입자. 시드·탈퇴 신청·탈퇴 회원 제외")
        AdminGenderCountResponse members,
        @Schema(description = "구독자. 이용권이 살아 있는 가입자 (유료 구독 + 무료 체험 + 선착순 무료)")
        Subscribers subscribers,
        @Schema(description = "탈퇴자. 탈퇴 완료 + 탈퇴 유예, 시드 제외")
        Withdrawn withdrawn,
        @Schema(description = "가입자가 있는 학교. 가입자 많은 순")
        List<Organization> organizations,
        @Schema(description = "가입자의 출생연도별 가입자·구독자 수. 최근 연도부터")
        List<BirthYear> birthYears,
        @Schema(description = "생년월일을 읽을 수 없어 출생연도 집계에서 빠진 가입자 수")
        Long unknownBirthYearMembers
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
                result.unknownBirthYearMembers()
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

    public record BirthYear(
            Integer year,
            Long members,
            Long subscribers
    ) {
    }
}
