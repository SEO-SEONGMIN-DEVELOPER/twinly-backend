package com.nidus.twinly.common.security;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.subscription.RevenueCatProperties;
import com.nidus.twinly.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({AdminProperties.class, RevenueCatProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] ANON_SESSION_PATHS = {
            "/api/v1/onboarding/**",
            "/api/v1/auth/onboarding/**",
            "/api/v1/auth/signup"
    };

    private static final String[] PUBLIC_ONBOARDING_PATHS = {
            "/api/v1/onboarding/organizations",
            "/api/v1/onboarding/survey-questions"
    };

    private static final String[] ADMIN_PATHS = {
            "/admin/**"
    };

    private static final String[] WEBHOOK_PATHS = {
            "/webhook/v1/**"
    };

    private final UserService userService;
    private final AnonService anonService;
    private final AdminProperties adminProperties;
    private final RevenueCatProperties revenueCatProperties;
    private final JsonMapper jsonMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        SecurityErrorResponder errorResponder = new SecurityErrorResponder(jsonMapper);
        RequestMatcher anonSessionPaths = pathMatcher(ANON_SESSION_PATHS);

        return http
                .csrf(csrf -> csrf.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ONBOARDING_PATHS).permitAll()
                        .requestMatchers(ANON_SESSION_PATHS).hasRole("ANON")
                        .requestMatchers(ADMIN_PATHS).hasRole("ADMIN")
                        .requestMatchers(WEBHOOK_PATHS).hasRole("WEBHOOK")
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/anon/**").permitAll()
                        .requestMatchers("/api/v1/legal/**").permitAll()
                        .requestMatchers("/internal/v1/**").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errorResponder)
                        .accessDeniedHandler(errorResponder))
                .addFilterBefore(new JwtAuthenticationFilter(userService, anonSessionPaths),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new AnonAuthenticationFilter(anonService, anonSessionPaths),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new AdminTokenAuthenticationFilter(adminProperties.apiToken(), pathMatcher(ADMIN_PATHS)),
                        JwtAuthenticationFilter.class)
                .addFilterBefore(new RevenueCatWebhookAuthenticationFilter(revenueCatProperties.webhookSecret(), pathMatcher(WEBHOOK_PATHS)),
                        JwtAuthenticationFilter.class)
                .build();
    }

    private RequestMatcher pathMatcher(String[] patterns) {
        return new OrRequestMatcher(Arrays.stream(patterns)
                .map(pattern -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(pattern))
                .toList());
    }
}
