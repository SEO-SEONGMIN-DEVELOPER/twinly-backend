package com.nidus.twinly.purchase.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nidus.twinly.purchase.domain.RevenueCatEnvironment;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RevenueCatSubscriberBody(
        Subscriber subscriber
) {

    public List<RevenueCatEntitlement> entitlementsIn(RevenueCatEnvironment environment) {
        if (subscriber == null || subscriber.entitlements() == null) {
            return List.of();
        }

        return subscriber.entitlements().entrySet().stream()
                .filter(entry -> subscriber.purchasedIn(entry.getValue().productIdentifier(), environment))
                .map(entry -> new RevenueCatEntitlement(entry.getKey(), entry.getValue().expiresDate()))
                .toList();
    }

    public record Subscriber(
            Map<String, Entitlement> entitlements,
            Map<String, Purchase> subscriptions,
            @JsonProperty("non_subscriptions")
            Map<String, List<Purchase>> nonSubscriptions
    ) {

        boolean purchasedIn(String productIdentifier, RevenueCatEnvironment environment) {
            Purchase subscription = subscriptions == null ? null : subscriptions.get(productIdentifier);
            if (subscription != null && subscription.isSandbox() != null) {
                return environment.accepts(subscription.environment());
            }

            List<Purchase> purchases = nonSubscriptions == null ? null : nonSubscriptions.get(productIdentifier);
            if (purchases != null && purchases.stream().anyMatch(purchase -> purchase.isSandbox() != null)) {
                return purchases.stream()
                        .filter(purchase -> purchase.isSandbox() != null)
                        .anyMatch(purchase -> environment.accepts(purchase.environment()));
            }

            return true;
        }
    }

    public record Entitlement(
            @JsonProperty("expires_date")
            Instant expiresDate,
            @JsonProperty("product_identifier")
            String productIdentifier
    ) {
    }

    public record Purchase(
            @JsonProperty("is_sandbox")
            Boolean isSandbox
    ) {

        RevenueCatEnvironment environment() {
            return isSandbox ? RevenueCatEnvironment.SANDBOX : RevenueCatEnvironment.PRODUCTION;
        }
    }
}
