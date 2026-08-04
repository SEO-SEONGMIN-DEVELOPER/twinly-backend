package com.nidus.twinly.common.security;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterUnitTest {

    private static final RequestMatcher NOT_ANON_PATH = request -> false;
    private static final RequestMatcher ANON_PATH = request -> true;

    @Mock
    UserService userService;

    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 액세스 토큰이면 UserInfo를 principal로, ROLE_USER를 권한으로 하는 인증을 컨텍스트에 담는다")
    void authenticate_storesUserInfoAndRoleUser() throws Exception {
        // given: 유저 42로 해석되는 토큰
        given(userService.resolveByAccessToken("valid-token")).willReturn(new UserInfo(42L));
        request.addHeader("Authorization", "Bearer valid-token");

        // when
        new JwtAuthenticationFilter(userService, NOT_ANON_PATH).doFilter(request, response, filterChain);

        // then: principal과 권한이 채워지고 요청은 다음 필터로 넘어간다
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new UserInfo(42L));
        assertThat(authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority))
                .containsExactly("ROLE_USER");
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 토큰 해석을 시도하지 않고 인증 없이 다음 필터로 넘긴다")
    void authenticate_skipsWhenHeaderMissing() throws Exception {
        // when
        new JwtAuthenticationFilter(userService, NOT_ANON_PATH).doFilter(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isSameAs(request);
        then(userService).should(never()).resolveByAccessToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("Bearer 스킴이 아니면 토큰 해석을 시도하지 않고 인증 없이 다음 필터로 넘긴다")
    void authenticate_skipsWhenNotBearerScheme() throws Exception {
        // given
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        // when
        new JwtAuthenticationFilter(userService, NOT_ANON_PATH).doFilter(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isSameAs(request);
        then(userService).should(never()).resolveByAccessToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("토큰이 유효하지 않아도 예외를 던지지 않고 인증 없이 다음 필터로 넘겨 기존 401 처리를 그대로 살린다")
    void authenticate_swallowsBusinessExceptionAndContinues() throws Exception {
        // given: 익명 세션 토큰이나 만료 토큰처럼 액세스 토큰으로 해석되지 않는 값
        willThrow(new BusinessException(ErrorCode.INVALID_TOKEN))
                .given(userService).resolveByAccessToken("anon-uuid-token");
        request.addHeader("Authorization", "Bearer anon-uuid-token");

        // when
        new JwtAuthenticationFilter(userService, NOT_ANON_PATH).doFilter(request, response, filterChain);

        // then: 필터가 요청을 끊지 않아야 익명 세션 API와 기존 예외 응답이 그대로 동작한다
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("토큰 해석 실패 사유를 요청에 남겨 EntryPoint가 UNAUTHORIZED로 뭉뚱그리지 않게 한다")
    void authenticate_recordsErrorCodeForEntryPoint() throws Exception {
        // given: 탈퇴한 유저의 토큰
        willThrow(new BusinessException(ErrorCode.WITHDRAWN_USER))
                .given(userService).resolveByAccessToken("withdrawn-user-token");
        request.addHeader("Authorization", "Bearer withdrawn-user-token");

        // when
        new JwtAuthenticationFilter(userService, NOT_ANON_PATH).doFilter(request, response, filterChain);

        // then
        assertThat(request.getAttribute(SecurityErrorResponder.ERROR_CODE_ATTRIBUTE))
                .isEqualTo(ErrorCode.WITHDRAWN_USER);
    }

    @Test
    @DisplayName("익명 세션 경로에서는 아예 동작하지 않아 익명 토큰을 액세스 토큰으로 오해하지 않는다")
    void authenticate_skipsAnonSessionPaths() throws Exception {
        // given: 익명 세션 경로로 들어온 요청
        request.addHeader("Authorization", "Bearer 550e8400-e29b-41d4-a716-446655440000");

        // when
        new JwtAuthenticationFilter(userService, ANON_PATH).doFilter(request, response, filterChain);

        // then: 해석을 시도하지 않고 실패 사유도 남기지 않는다
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(SecurityErrorResponder.ERROR_CODE_ATTRIBUTE)).isNull();
        assertThat(filterChain.getRequest()).isSameAs(request);
        then(userService).should(never()).resolveByAccessToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("이미 인증이 담겨 있으면 토큰을 다시 해석하지 않고 기존 인증을 유지한다")
    void authenticate_keepsExistingAuthentication() throws Exception {
        // given
        Authentication existing = new UsernamePasswordAuthenticationToken(new UserInfo(7L), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        request.addHeader("Authorization", "Bearer valid-token");

        // when
        new JwtAuthenticationFilter(userService, NOT_ANON_PATH).doFilter(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
        then(userService).should(never()).resolveByAccessToken(org.mockito.ArgumentMatchers.anyString());
    }
}
