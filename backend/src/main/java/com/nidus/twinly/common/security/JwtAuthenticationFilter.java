package com.nidus.twinly.common.security;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.user.service.UserService;
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

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final SimpleGrantedAuthority ROLE_USER = new SimpleGrantedAuthority("ROLE_USER");

    private final UserService userService;
    private final RequestMatcher anonSessionPaths;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return anonSessionPaths.matches(request);
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

        UserInfo userInfo;
        try {
            userInfo = userService.resolveByAccessToken(authorizationHeader.substring(BEARER_PREFIX.length()));
        } catch (BusinessException e) {
            request.setAttribute(SecurityErrorResponder.ERROR_CODE_ATTRIBUTE, e.getErrorCode());
            return;
        }

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(userInfo, null, List.of(ROLE_USER)));
    }
}
