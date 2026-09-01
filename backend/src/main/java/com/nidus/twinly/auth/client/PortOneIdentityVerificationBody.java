package com.nidus.twinly.auth.client;

public record PortOneIdentityVerificationBody(
        PortOneIdentityVerificationStatus status,
        Channel channel,
        VerifiedCustomer verifiedCustomer
) {

    public record Channel(
            PortOneChannelType type
    ) {
    }

    public record VerifiedCustomer(
            String name,
            String birthDate,
            String gender,
            String phoneNumber,
            String ci
    ) {
    }
}
