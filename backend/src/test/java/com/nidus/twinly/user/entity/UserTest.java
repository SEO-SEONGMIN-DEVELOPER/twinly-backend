package com.nidus.twinly.user.entity;

import com.nidus.twinly.common.domain.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    @DisplayName("탈퇴하지 않은 유저의 표시 이름은 성+이름이고, 목록용 표시 이름은 이름만이다")
    void display_names_of_active_user() {
        // given: 탈퇴하지 않은 유저
        User user = user(null);

        // then: 두 표기 형식은 화면마다 의도적으로 다르다
        assertThat(user.displayName()).isEqualTo("홍길동");
        assertThat(user.displayGivenName()).isEqualTo("길동");
    }

    @Test
    @DisplayName("탈퇴한 유저는 두 표기 모두 '탈퇴한 사용자'로 마스킹된다")
    void display_names_of_withdrawn_user() {
        // given: deletedAt이 채워진 유저
        User user = user(Instant.now());

        // then: 표기 형식과 무관하게 실명이 노출되지 않는다
        assertThat(user.displayName()).isEqualTo(User.WITHDRAWN_NAME);
        assertThat(user.displayGivenName()).isEqualTo(User.WITHDRAWN_NAME);
    }

    private User user(Instant deletedAt) {
        User user = User.create(
                "nick", "홍", "familyHash", "길동", "givenHash",
                Gender.MALE, "aff", "affHash", "affNo", "affNoHash",
                "2000-01-01", "birthHash", "phone", "phoneHash", "email", "emailHash");
        ReflectionTestUtils.setField(user, "deletedAt", deletedAt);
        return user;
    }
}
