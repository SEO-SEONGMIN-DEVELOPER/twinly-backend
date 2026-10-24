package com.nidus.twinly.admin.dto.response;

import com.nidus.twinly.admin.dto.result.AdminBatchCostResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminBatchCostResponse(
        @Schema(description = "전체 합계를 낸 배치 날짜(가장 최근에 시작한 날짜). 배치가 한 번도 없으면 null")
        LocalDate simulationDate,
        @Schema(description = "그룹 전체 수")
        Integer groupCount,
        @Schema(description = "simulationDate 배치를 시작한 그룹 수. 전체 합계는 이 그룹들만 더한 값")
        Integer startedGroupCount,
        @Schema(description = "시작한 그룹들의 참가자 합")
        Integer participantCount,
        @Schema(description = "시작한 그룹들의 비용 합 (USD)")
        BigDecimal totalCostUsd,
        @Schema(description = "전체 비용 ÷ 전체 참가자 (USD). 그룹 인당 비용의 평균이 아니다")
        BigDecimal averageCostUsd,
        @Schema(description = "금액을 몰라 합계에 못 넣은 응답 수. 0보다 크면 실제 비용이 표시값보다 크다")
        Integer unpricedResponseCount,
        @Schema(description = "그룹 번호 오름차순")
        List<Group> groups
) {

    public static AdminBatchCostResponse from(AdminBatchCostResult result) {
        return new AdminBatchCostResponse(
                result.simulationDate(),
                result.groupCount(),
                result.startedGroupCount(),
                result.participantCount(),
                result.totalCostUsd(),
                result.averageCostUsd(),
                result.unpricedResponseCount(),
                result.groups().stream().map(Group::from).toList()
        );
    }

    public record Group(
            Integer groupNumber,
            @Schema(description = "그 그룹 세계의 현재 시각. 시간대 없는 KST")
            LocalDateTime worldTime,
            @Schema(description = "그 그룹 비용이 어느 배치 날짜 것인지. 아직 배치를 안 돌린 그룹은 null")
            LocalDate simulationDate,
            @Schema(description = "그 날짜 그 그룹 참가자 수. 날짜가 null 이면 null")
            Integer participantCount,
            @Schema(description = "그 그룹의 그 날짜 비용 (USD). 배치를 안 돌린 그룹은 null")
            BigDecimal totalCostUsd,
            @Schema(description = "그 그룹 인당 비용 (USD). 배치를 안 돌린 그룹은 null")
            BigDecimal averageCostUsd,
            @Schema(description = "그 그룹 금액 미확인 응답 수. 배치를 안 돌린 그룹은 null")
            Integer unpricedResponseCount,
            @Schema(description = "지금 그 그룹에서 시뮬레이션 대상인 회원의 성별 수(탈퇴 아님·이용권 유효·필수 동의 완료, 시드 포함). participantCount 는 배치 날짜 기준이라 다를 수 있다")
            AdminGenderCountResponse members
    ) {

        static Group from(AdminBatchCostResult.Group group) {
            return new Group(
                    group.groupNumber(),
                    group.worldTime(),
                    group.simulationDate(),
                    group.participantCount(),
                    group.totalCostUsd(),
                    group.averageCostUsd(),
                    group.unpricedResponseCount(),
                    AdminGenderCountResponse.from(group.members())
            );
        }
    }
}
