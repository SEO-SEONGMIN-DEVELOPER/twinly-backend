package com.nidus.twinly.common.security;

import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class AnonAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final SimpleGrantedAuthority ROLE_ANON = new SimpleGrantedAuthority("ROLE_ANON");

    private final AnonService anonService;
    private final RequestMatcher anonSessionPaths;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !anonSessionPaths.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        authenticate(request);

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return;
        }

        AnonSessionSnapshot snapshot;
        try {
            snapshot = anonService.resolveByToken(parseToken(authorizationHeader.substring(BEARER_PREFIX.length())));
        } catch (BusinessException e) {
            request.setAttribute(SecurityErrorResponder.ERROR_CODE_ATTRIBUTE, e.getErrorCode());
            return;
        }

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(snapshot, null, List.of(ROLE_ANON)));
    }

    private UUID parseToken(String token) {
        try {
            return UUID.fromString(token);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, e);
        }
    }
}
