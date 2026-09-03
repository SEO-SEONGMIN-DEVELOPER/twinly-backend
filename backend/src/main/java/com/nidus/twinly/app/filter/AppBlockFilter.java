package com.nidus.twinly.app.filter;

import com.nidus.twinly.app.domain.AppBlockPolicy;
import com.nidus.twinly.app.domain.AppPlatform;
import com.nidus.twinly.app.domain.AppVersion;
import com.nidus.twinly.app.domain.AppVersionPolicy;
import com.nidus.twinly.app.domain.MaintenanceState;
import com.nidus.twinly.app.dto.response.AppUpdateRequiredResponse;
import com.nidus.twinly.app.dto.response.MaintenanceResponse;
import com.nidus.twinly.app.store.AppBlockPolicyStore;
import com.nidus.twinly.common.web.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@RequiredArgsConstructor
public class AppBlockFilter extends OncePerRequestFilter {

    public static final String PLATFORM_HEADER = "X-App-Platform";
    public static final String VERSION_HEADER = "X-App-Version";

    private static final RequestMatcher APP_PATHS = PathPatternRequestMatcher.withDefaults().matcher("/api/**");
    private static final String CACHE_CONTROL_NO_STORE = "no-store";

    private final AppBlockPolicyStore appBlockPolicyStore;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !APP_PATHS.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        AppBlockPolicy policy = appBlockPolicyStore.current();

        MaintenanceState maintenance = policy.maintenance();
        if (maintenance.active()) {
            writeMaintenance(response, maintenance);
            return;
        }

        Optional<AppVersionPolicy> outdated = findOutdatedPolicy(request, policy);
        if (outdated.isPresent()) {
            writeUpdateRequired(response, outdated.get());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Optional<AppVersionPolicy> findOutdatedPolicy(HttpServletRequest request, AppBlockPolicy policy) {
        Optional<AppPlatform> platform = AppPlatform.fromHeader(request.getHeader(PLATFORM_HEADER));
        Optional<AppVersion> version = AppVersion.parse(request.getHeader(VERSION_HEADER));

        if (platform.isEmpty() || version.isEmpty()) {
            return Optional.empty();
        }

        return policy.versionPolicyOf(platform.get())
                .filter(versionPolicy -> version.get().isLowerThan(versionPolicy.minVersion()));
    }

    private void writeMaintenance(HttpServletResponse response, MaintenanceState maintenance) throws IOException {
        String message = maintenance.message() != null
                ? maintenance.message()
                : ErrorCode.MAINTENANCE.getDefaultMessage();

        retryAfterSeconds(maintenance.until())
                .ifPresent(seconds -> response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(seconds)));

        write(response, HttpStatus.SERVICE_UNAVAILABLE, MaintenanceResponse.of(message, maintenance.until()));
    }

    private void writeUpdateRequired(HttpServletResponse response, AppVersionPolicy versionPolicy) throws IOException {
        write(response, HttpStatus.UPGRADE_REQUIRED,
                AppUpdateRequiredResponse.of(versionPolicy.storeUrl(), versionPolicy.minVersion().toString()));
    }

    private Optional<Long> retryAfterSeconds(Instant until) {
        if (until == null) {
            return Optional.empty();
        }

        long seconds = Duration.between(clock.instant(), until).getSeconds();

        return seconds > 0 ? Optional.of(seconds) : Optional.empty();
    }

    private void write(HttpServletResponse response, HttpStatus status, Object body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_NO_STORE);
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }
}
