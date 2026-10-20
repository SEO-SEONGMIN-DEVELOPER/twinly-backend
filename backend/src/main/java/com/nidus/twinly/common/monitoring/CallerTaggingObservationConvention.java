package com.nidus.twinly.common.monitoring;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class CallerTaggingObservationConvention extends DefaultServerRequestObservationConvention {

    static final String CALLER = "caller";

    private static final Set<String> NICE_PATHS = Set.of(
            "/api/v1/auth/onboarding/identity/return",
            "/api/v1/auth/onboarding/identity/close"
    );

    @Override
    public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
        String path = context.getCarrier() != null ? context.getCarrier().getRequestURI() : null;

        return super.getLowCardinalityKeyValues(context).and(KeyValue.of(CALLER, resolveCaller(path)));
    }

    static String resolveCaller(String path) {
        if (path == null) {
            return "other";
        }
        if (NICE_PATHS.contains(path) || path.startsWith("/webhook/")) {
            return "external";
        }
        if (path.startsWith("/internal/")) {
            return "ai";
        }
        if (path.startsWith("/admin/")) {
            return "admin";
        }
        if (path.startsWith("/api/") || path.startsWith("/ws/")) {
            return "app";
        }
        return "other";
    }
}
