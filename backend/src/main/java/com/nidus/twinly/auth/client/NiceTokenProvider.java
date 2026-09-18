package com.nidus.twinly.auth.client;

import com.nidus.twinly.auth.config.NiceProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class NiceTokenProvider {

    private final NiceAuthClient niceAuthClient;
    private final Clock clock;
    private final Duration refreshMargin;

    private volatile NiceToken cached;

    public NiceTokenProvider(NiceAuthClient niceAuthClient, Clock clock, NiceProperties niceProperties) {
        this.niceAuthClient = niceAuthClient;
        this.clock = clock;
        this.refreshMargin = niceProperties.tokenRefreshMargin();
    }

    public NiceToken getToken() {
        NiceToken token = cached;
        Instant now = clock.instant();

        if (token != null && token.isUsableAt(now, refreshMargin)) {
            return token;
        }

        return refresh(now);
    }

    public void invalidate() {
        cached = null;
    }

    private synchronized NiceToken refresh(Instant now) {
        NiceToken token = cached;

        if (token != null && token.isUsableAt(now, refreshMargin)) {
            return token;
        }

        NiceToken issued = NiceToken.from(niceAuthClient.issueToken());
        cached = issued;

        return issued;
    }
}
