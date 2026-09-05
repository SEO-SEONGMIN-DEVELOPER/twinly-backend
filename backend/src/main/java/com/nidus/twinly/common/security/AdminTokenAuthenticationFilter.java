package com.nidus.twinly.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RequiredArgsConstructor
public class AdminTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
    private static final String ADMIN_PRINCIPAL = "admin";
    private static final SimpleGrantedAuthority ROLE_ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");

    private final String apiToken;
    private final RequestMatcher adminPaths;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !adminPaths.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        authenticate(request);

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        if (!StringUtils.hasText(apiToken)) {
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        String presentedToken = request.getHeader(ADMIN_TOKEN_HEADER);

        if (presentedToken == null || !matchesApiToken(presentedToken)) {
            return;
        }

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(ADMIN_PRINCIPAL, null, List.of(ROLE_ADMIN)));
    }

    private boolean matchesApiToken(String presentedToken) {
        return MessageDigest.isEqual(
                presentedToken.getBytes(StandardCharsets.UTF_8),
                apiToken.getBytes(StandardCharsets.UTF_8));
    }
}
