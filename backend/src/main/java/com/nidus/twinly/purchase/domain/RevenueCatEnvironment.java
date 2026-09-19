package com.nidus.twinly.purchase.domain;

public enum RevenueCatEnvironment {
    SANDBOX,
    PRODUCTION;

    public boolean accepts(RevenueCatEnvironment purchaseEnvironment) {
        return this == PRODUCTION || purchaseEnvironment == SANDBOX;
    }
}
