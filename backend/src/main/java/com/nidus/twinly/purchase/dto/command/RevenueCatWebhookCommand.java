package com.nidus.twinly.purchase.dto.command;

import com.nidus.twinly.purchase.dto.request.RevenueCatWebhookRequest;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

public record RevenueCatWebhookCommand(
        String eventId,
        String type,
        String appUserId,
        List<String> appUserIds,
        String environment
) {

    public static RevenueCatWebhookCommand from(RevenueCatWebhookRequest request) {
        RevenueCatWebhookRequest.Event event = request.event();

        if (event == null) {
            return new RevenueCatWebhookCommand(null, null, null, List.of(), null);
        }

        return new RevenueCatWebhookCommand(
                event.id(),
                event.type(),
                event.appUserId(),
                appUserIds(event),
                event.environment()
        );
    }

    private static List<String> appUserIds(RevenueCatWebhookRequest.Event event) {
        return Stream.of(
                        Stream.ofNullable(event.appUserId()),
                        stream(event.transferredFrom()),
                        stream(event.transferredTo()))
                .flatMap(Function.identity())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static Stream<String> stream(List<String> appUserIds) {
        return appUserIds == null ? Stream.empty() : appUserIds.stream();
    }
}
