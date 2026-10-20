package com.nidus.twinly.admin.dto.response;

import com.nidus.twinly.admin.domain.SubscriptionKind;
import com.nidus.twinly.admin.dto.result.AdminSubscriptionListResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminSubscriptionListResponse(
        @Schema(description = "종류별 활성 이용권 수")
        ActiveCounts activeCounts,
        AdminPageResponse<Item> page
) {

    public static AdminSubscriptionListResponse from(AdminSubscriptionListResult result) {
        return new AdminSubscriptionListResponse(
                new ActiveCounts(
                        result.activeCountByKind().getOrDefault(SubscriptionKind.PAID, 0L),
                        result.activeCountByKind().getOrDefault(SubscriptionKind.TRIAL, 0L),
                        result.activeCountByKind().getOrDefault(SubscriptionKind.EARLY_GRANT, 0L)),
                AdminPageResponse.from(result.page(), item -> new Item(
                        AdminMemberSummaryResponse.from(item.member()),
                        AdminSubscriptionResponse.from(item.subscription())))
        );
    }

    public record ActiveCounts(
            Long paid,
            Long trial,
            Long earlyGrant
    ) {
    }

    public record Item(
            @Schema(nullable = true)
            AdminMemberSummaryResponse member,
            AdminSubscriptionResponse subscription
    ) {
    }
}
