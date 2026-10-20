package com.nidus.twinly.admin.dto.result;

import com.nidus.twinly.admin.domain.SubscriptionKind;

import java.util.Map;

public record AdminSubscriptionListResult(
        Map<SubscriptionKind, Long> activeCountByKind,
        AdminPageResult<Item> page
) {

    public record Item(
            AdminMemberSummaryResult member,
            AdminSubscriptionResult subscription
    ) {
    }
}
