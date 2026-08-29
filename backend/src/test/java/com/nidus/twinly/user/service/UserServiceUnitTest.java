package com.nidus.twinly.user.service;

import com.nidus.twinly.common.jwt.JwtService;
import com.nidus.twinly.user.dto.result.UsersResult;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {

    @Mock
    JwtService jwtService;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    UserService userService;

    @Test
    @DisplayName("조회 결과가 limit 이하면 hasMore는 false이고 nextCursor는 null이다")
    void users_last_page() {
        // given: limit보다 적게 조회되는 마지막 페이지
        given(userRepository.findIdsAfterCursor(isNull(), eq(EntitlementReader.SIMULATION_ACCESS), any(), eq(3))).willReturn(List.of(1L, 2L));

        // when: limit 2로 유저 목록 조회
        UsersResult result = userService.users(null, 2);

        // then: 조회된 전부를 반환 + 마지막 페이지로 표시
        assertThat(result.userIds()).containsExactly(1L, 2L);
        assertThat(result.page().hasMore()).isFalse();
        assertThat(result.page().nextCursor()).isNull();
    }

    @Test
    @DisplayName("limit보다 하나 더 조회되면 초과분을 잘라내고 hasMore와 nextCursor를 채운다")
    void users_has_more() {
        // given: 다음 페이지 존재 여부 판별을 위해 limit+1개가 조회되는 상황
        given(userRepository.findIdsAfterCursor(eq(10L), eq(EntitlementReader.SIMULATION_ACCESS), any(), eq(3))).willReturn(List.of(11L, 12L, 13L));

        // when: 커서 10, limit 2로 유저 목록 조회
        UsersResult result = userService.users(10L, 2);

        // then: limit만큼만 반환 + 마지막 id가 다음 커서
        assertThat(result.userIds()).containsExactly(11L, 12L);
        assertThat(result.page().hasMore()).isTrue();
        assertThat(result.page().nextCursor()).isEqualTo(12L);
    }

    @Test
    @DisplayName("limit이 없으면 기본값 500으로 조회한다")
    void users_without_limit_uses_default() {
        // given: limit 미지정 시 기본값(500)에 판별용 1건을 더한 501건을 조회
        given(userRepository.findIdsAfterCursor(isNull(), eq(EntitlementReader.SIMULATION_ACCESS), any(), eq(501))).willReturn(List.of(1L));

        // when: limit 없이 유저 목록 조회
        userService.users(null, null);

        // then: 기본값 기준으로 리포지토리에 위임
        then(userRepository).should().findIdsAfterCursor(isNull(), eq(EntitlementReader.SIMULATION_ACCESS), any(), eq(501));
    }

    @Test
    @DisplayName("조회 결과가 없으면 빈 목록과 함께 마지막 페이지로 반환한다")
    void users_empty() {
        // given: 커서 이후 유저가 없는 상황
        given(userRepository.findIdsAfterCursor(eq(99L), eq(EntitlementReader.SIMULATION_ACCESS), any(), eq(501))).willReturn(List.of());

        // when: 커서 99로 유저 목록 조회
        UsersResult result = userService.users(99L, null);

        // then: 빈 목록 + 마지막 페이지로 표시
        assertThat(result.userIds()).isEmpty();
        assertThat(result.page().hasMore()).isFalse();
        assertThat(result.page().nextCursor()).isNull();
    }
}
