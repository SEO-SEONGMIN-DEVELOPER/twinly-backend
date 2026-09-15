package com.nidus.twinly.auth.client;

import java.time.Duration;
import java.time.Instant;

public record NiceToken(
        String accessToken,
        String ticket,
        int iterators,
        Instant expiresAt
) {

    public static NiceToken from(NiceTokenBody body) {
        return new NiceToken(
                body.accessToken(),
                body.ticket(),
                body.iterators(),
                Instant.ofEpochMilli(body.expiresIn()));
    }

    public boolean isUsableAt(Instant now, Duration margin) {
        return now.plus(margin).isBefore(expiresAt);
    }
}
