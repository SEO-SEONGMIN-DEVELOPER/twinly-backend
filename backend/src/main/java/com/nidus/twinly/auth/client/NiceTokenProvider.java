package com.nidus.twinly.auth.client;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class NiceTokenProvider {

    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(1);

    private final NiceAuthClient niceAuthClient;
    private final Clock clock;

    private volatile NiceToken cached;

    public NiceTokenProvider(NiceAuthClient niceAuthClient, Clock clock) {
        this.niceAuthClient = niceAuthClient;
        this.clock = clock;
    }

    public NiceToken getToken() {
        NiceToken token = cached;
        Instant now = clock.instant();

        if (token != null && token.isUsableAt(now, REFRESH_MARGIN)) {
            return token;
        }

        return refresh(now);
    }

    public void invalidate() {
        cached = null;
    }

    private synchronized NiceToken refresh(Instant now) {
        NiceToken token = cached;

        if (token != null && token.isUsableAt(now, REFRESH_MARGIN)) {
            return token;
        }

        NiceToken issued = NiceToken.from(niceAuthClient.issueToken());
        cached = issued;

        return issued;
    }
}
