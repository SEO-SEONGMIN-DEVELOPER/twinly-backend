package com.nidus.twinly.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTokenAuthenticationFilterUnitTest {

    private static final String API_TOKEN = "super-secret-admin-token";

    private static final RequestMatcher ADMIN_PATH = request -> true;
    private static final RequestMatcher NOT_ADMIN_PATH = request -> false;

    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("설정값과 일치하는 토큰이면 ROLE_ADMIN 권한을 담는다")
    void authenticate_grantsRoleAdminOnMatchingToken() throws Exception {
        // given
        request.addHeader("X-Admin-Token", API_TOKEN);

        // when
        new AdminTokenAuthenticationFilter(API_TOKEN, ADMIN_PATH).doFilter(request, response, filterChain);

        // then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo("admin");
        assertThat(authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority))
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("서버에 관리자 토큰이 설정되지 않았으면 빈 헤더를 보내도 권한을 주지 않는다")
    void authenticate_deniesEveryoneWhenApiTokenNotConfigured() throws Exception {
        // given: 설정 누락 상태에서 빈 값으로 통과를 노리는 요청
        request.addHeader("X-Admin-Token", "");

        // when
        new AdminTokenAuthenticationFilter("", ADMIN_PATH).doFilter(request, response, filterChain);

        // then: 설정 누락이 곧 개방이 되어서는 안 된다
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("토큰이 일치하지 않으면 예외 없이 권한 없는 상태로 다음 필터에 넘긴다")
    void authenticate_skipsOnMismatchedToken() throws Exception {
        // given
        request.addHeader("X-Admin-Token", "wrong-token");

        // when
        new AdminTokenAuthenticationFilter(API_TOKEN, ADMIN_PATH).doFilter(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("헤더가 없으면 권한을 담지 않는다")
    void authenticate_skipsWhenHeaderMissing() throws Exception {
        // when
        new AdminTokenAuthenticationFilter(API_TOKEN, ADMIN_PATH).doFilter(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("관리자 경로가 아니면 올바른 토큰을 보내도 동작하지 않는다")
    void authenticate_skipsNonAdminPaths() throws Exception {
        // given: 일반 API에 관리자 토큰을 실어 보내는 요청
        request.addHeader("X-Admin-Token", API_TOKEN);

        // when
        new AdminTokenAuthenticationFilter(API_TOKEN, NOT_ADMIN_PATH).doFilter(request, response, filterChain);

        // then: 관리자 권한이 관리자 경로 밖으로 새지 않는다
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("이미 인증이 담겨 있으면 덮어쓰지 않는다")
    void authenticate_keepsExistingAuthentication() throws Exception {
        // given
        Authentication existing = new UsernamePasswordAuthenticationToken("someone", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        request.addHeader("X-Admin-Token", API_TOKEN);

        // when
        new AdminTokenAuthenticationFilter(API_TOKEN, ADMIN_PATH).doFilter(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    }
}
