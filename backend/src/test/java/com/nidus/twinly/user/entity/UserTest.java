package com.nidus.twinly.user.entity;

import com.nidus.twinly.common.domain.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    @DisplayName("탈퇴하지 않은 유저의 표시 이름은 내 프로필용 성+이름과 그 외 화면용 닉네임으로 나뉜다")
    void display_names_of_active_user() {
        // given: 탈퇴하지 않은 유저
        User user = user(null);

        // then: 두 표기 형식은 화면마다 의도적으로 다르다
        assertThat(user.displayFullName()).isEqualTo("홍길동");
        assertThat(user.displayNickname()).isEqualTo("nick");
    }

    @Test
    @DisplayName("탈퇴한 유저는 두 표기 모두 '탈퇴한 사용자'로 마스킹된다")
    void display_names_of_withdrawn_user() {
        // given: deletedAt이 채워진 유저
        User user = user(Instant.now());

        // then: 표기 형식과 무관하게 실명이 노출되지 않는다
        assertThat(user.displayFullName()).isEqualTo(User.WITHDRAWN_NAME);
        assertThat(user.displayNickname()).isEqualTo(User.WITHDRAWN_NAME);
    }

    @Test
    @DisplayName("탈퇴 유예 기간 중인 유저도 파기 전부터 탈퇴한 유저로 간주된다")
    void withdrawal_requested_user_is_withdrawn() {
        // given: 탈퇴를 신청했지만 아직 파기되지 않은 유저
        User user = user(null);
        user.requestWithdrawal(Duration.ofDays(15));

        // then: 탈퇴로 판정되고 닉네임이 노출되지 않는다
        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.displayNickname()).isEqualTo(User.WITHDRAWN_NAME);
    }

    @Test
    @DisplayName("탈퇴를 철회하면 다시 탈퇴하지 않은 유저로 간주된다")
    void cancelled_withdrawal_user_is_not_withdrawn() {
        // given: 탈퇴 신청 후 철회한 유저
        User user = user(null);
        user.requestWithdrawal(Duration.ofDays(15));
        user.cancelWithdrawal();

        // then: 탈퇴 판정이 풀리고 닉네임이 다시 표시된다
        assertThat(user.isWithdrawn()).isFalse();
        assertThat(user.displayNickname()).isEqualTo("nick");
    }

    @Test
    @DisplayName("출생 연도는 생년월일에서 연도 뒤 두 자리만 남긴다")
    void short_birth_year_keeps_last_two_digits_of_year() {
        // given: 1998년생 유저
        User user = user(null);
        ReflectionTestUtils.setField(user, "birthDate", "1998-07-15");

        // then: 연도 뒤 두 자리
        assertThat(user.shortBirthYear()).isEqualTo("98");
    }

    @Test
    @DisplayName("파기되어 생년월일이 연도만 남아도 출생 연도 두 자리는 앞의 0을 유지한 채 그대로 나온다")
    void short_birth_year_after_delete() {
        // given: 2002년생 유저가 파기되어 생년월일이 연도로 일반화된 상태
        User user = user(null);
        ReflectionTestUtils.setField(user, "birthDate", "2002-03-15");
        user.delete();

        // then: 일반화 전과 같은 두 자리
        assertThat(user.shortBirthYear()).isEqualTo("02");
    }

    @Test
    @DisplayName("소속이 대학교로 끝나면 학교를 떼고, 그 밖의 소속은 그대로 둔다")
    void short_organization_of_university() {
        // given: 대학교 소속 유저와 고등학교 소속 유저
        User university = user(null);
        ReflectionTestUtils.setField(university, "organization", "성균관대학교");
        User highSchool = user(null);
        ReflectionTestUtils.setField(highSchool, "organization", "한국고등학교");

        // then: 성균관대학교 → 성균관대, 한국고등학교는 그대로
        assertThat(university.shortOrganization()).isEqualTo("성균관대");
        assertThat(highSchool.shortOrganization()).isEqualTo("한국고등학교");
    }

    @Test
    @DisplayName("소속이 여자대학교로 끝나면 여대로 줄인다")
    void short_organization_of_womens_university() {
        // given: 여자대학교 소속 유저
        User user = user(null);
        ReflectionTestUtils.setField(user, "organization", "성신여자대학교");

        // then: 성신여자대학교 → 성신여대
        assertThat(user.shortOrganization()).isEqualTo("성신여대");
    }

    private User user(Instant deletedAt) {
        User user = User.create(
                "nick", "홍", "familyHash", "길동", "givenHash",
                Gender.MALE, "organization", "organizationHash", "aff", "affHash", "affNo", "affNoHash",
                "2000-01-01", "birthHash", "phone", "phoneHash", "email", "emailHash", null, null, null, null);
        ReflectionTestUtils.setField(user, "deletedAt", deletedAt);
        return user;
    }
}
