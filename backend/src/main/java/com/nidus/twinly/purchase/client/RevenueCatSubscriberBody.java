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
                .map(entry -> toEntitlement(entry.getKey(), entry.getValue()))
                .toList();
    }

    public RevenueCatSubscriptions subscriptionsIn(RevenueCatEnvironment environment, String entitlementName) {
        if (subscriber == null) {
            return new RevenueCatSubscriptions(null, List.of());
        }

        return new RevenueCatSubscriptions(
                entitlementExpiresAt(environment, entitlementName),
                subscriber.subscriptions() == null ? List.of() : subscriber.subscriptions().entrySet().stream()
                        .filter(entry -> entry.getValue().expiresDate() != null)
                        .filter(entry -> entry.getValue().isPromotional() || entry.getValue().isSandbox() == null || environment.accepts(entry.getValue().environment()))
                        .map(entry -> entry.getValue().toSubscription(entry.getKey()))
                        .toList());
    }

    private Instant entitlementExpiresAt(RevenueCatEnvironment environment, String entitlementName) {
        return entitlementsIn(environment).stream()
                .filter(entitlement -> entitlement.entitlement().equals(entitlementName))
                .map(RevenueCatEntitlement::expiresAt)
                .findFirst()
                .orElse(null);
    }

    private RevenueCatEntitlement toEntitlement(String name, Entitlement entitlement) {
        Purchase purchase = subscriber.purchaseOf(entitlement.productIdentifier());

        return new RevenueCatEntitlement(
                name,
                entitlement.expiresDate(),
                purchase == null ? null : purchase.store(),
                purchase == null ? null : purchase.periodType());
    }

    public record Subscriber(
            Map<String, Entitlement> entitlements,
            Map<String, Purchase> subscriptions,
            @JsonProperty("non_subscriptions")
            Map<String, List<Purchase>> nonSubscriptions
    ) {

        Purchase purchaseOf(String productIdentifier) {
            Purchase subscription = subscriptions == null ? null : subscriptions.get(productIdentifier);

            if (subscription != null) {
                return subscription;
            }

            List<Purchase> purchases = nonSubscriptions == null ? null : nonSubscriptions.get(productIdentifier);

            return purchases == null || purchases.isEmpty() ? null : purchases.getLast();
        }

        boolean purchasedIn(String productIdentifier, RevenueCatEnvironment environment) {
            Purchase subscription = subscriptions == null ? null : subscriptions.get(productIdentifier);
            if (subscription != null && subscription.isPromotional()) {
                return true;
            }
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
            Boolean isSandbox,
            String store,
            @JsonProperty("period_type")
            String periodType,
            @JsonProperty("purchase_date")
            Instant purchaseDate,
            @JsonProperty("expires_date")
            Instant expiresDate,
            @JsonProperty("unsubscribe_detected_at")
            Instant unsubscribeDetectedAt,
            @JsonProperty("billing_issues_detected_at")
            Instant billingIssuesDetectedAt,
            @JsonProperty("grace_period_expires_date")
            Instant gracePeriodExpiresDate
    ) {

        private static final String PROMOTIONAL_STORE = "promotional";

        boolean isPromotional() {
            return PROMOTIONAL_STORE.equals(store);
        }

        RevenueCatEnvironment environment() {
            return isSandbox ? RevenueCatEnvironment.SANDBOX : RevenueCatEnvironment.PRODUCTION;
        }

        RevenueCatSubscription toSubscription(String productId) {
            return new RevenueCatSubscription(
                    productId,
                    store,
                    periodType,
                    purchaseDate,
                    expiresDate,
                    unsubscribeDetectedAt,
                    billingIssuesDetectedAt,
                    gracePeriodExpiresDate);
        }
    }
}
