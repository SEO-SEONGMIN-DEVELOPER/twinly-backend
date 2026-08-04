package com.nidus.twinly.user.integration;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.service.WithdrawnUserDeletionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WithdrawnUserDeletionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    WithdrawnUserDeletionService withdrawnUserDeletionService;

    @PersistenceContext
    EntityManager entityManager;

    /** 유예가 이미 만료된 상태를 만든다 (음수 유예 기간 → withdrawalScheduledAt이 과거). */
    private User saveWithdrawalExpiredUser() {
        User user = saveUser();
        user.requestWithdrawal(Duration.ofDays(-1));
        return user;
    }

    /** 저장된 변경분을 DB에 반영하고 영속성 컨텍스트를 비워, 이후 조회가 실제 DB 상태를 읽게 한다. */
    private User reload(Long userId) {
        entityManager.flush();
        entityManager.clear();
        return userRepository.findById(userId).orElseThrow();
    }

    @Test
    @DisplayName("유예 만료 유저 파기: 식별 정보는 지워지고 생년월일은 연도만 남으며 학과·성별은 보존된다")
    void deletes_identifiers_of_expired_user() {
        // given: 유예 기간이 만료된 탈퇴 신청 유저
        User expired = saveWithdrawalExpiredUser();

        // when: 파기 배치 실행
        withdrawnUserDeletionService.deleteAll();

        // then: 파기 대상 컬럼은 전부 비워지고 deletedAt이 기록된다
        User deleted = reload(expired.getId());
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.isWithdrawn()).isTrue();
        assertThat(deleted.getNickname()).isNull();
        assertThat(deleted.getFamilyName()).isNull();
        assertThat(deleted.getFamilyNameHash()).isNull();
        assertThat(deleted.getGivenName()).isNull();
        assertThat(deleted.getGivenNameHash()).isNull();
        assertThat(deleted.getAffiliationNumber()).isNull();
        assertThat(deleted.getAffiliationNumberHash()).isNull();
        assertThat(deleted.getPhoneNumber()).isNull();
        assertThat(deleted.getPhoneNumberHash()).isNull();
        assertThat(deleted.getEmail()).isNull();
        assertThat(deleted.getEmailHash()).isNull();
        assertThat(deleted.getBirthDateHash()).isNull();

        // then: 생년월일은 연도까지만 일반화되고, 보존 대상은 그대로 남는다
        assertThat(deleted.getBirthDate()).isEqualTo("2000");
        assertThat(deleted.getGender()).isEqualTo(Gender.MALE);
        assertThat(deleted.getAffiliation()).isNotNull();
        assertThat(deleted.getCreatedAt()).isNotNull();
        assertThat(deleted.getWithdrawalRequestedAt()).isNotNull();
        assertThat(deleted.getWithdrawalScheduledAt()).isNotNull();
    }

    @Test
    @DisplayName("유예 기간 중 유저는 파기되지 않는다")
    void keeps_user_within_grace_period() {
        // given: 탈퇴를 신청했지만 유예 기간이 남은 유저
        User pending = saveUser();
        pending.requestWithdrawal(Duration.ofDays(15));

        // when: 파기 배치 실행
        withdrawnUserDeletionService.deleteAll();

        // then: 아무것도 지워지지 않는다
        User reloaded = reload(pending.getId());
        assertThat(reloaded.getDeletedAt()).isNull();
        assertThat(reloaded.getPhoneNumber()).isNotNull();
        assertThat(reloaded.getBirthDate()).isEqualTo("2000-01-01");
    }

    @Test
    @DisplayName("탈퇴를 신청하지 않은 유저는 파기 대상이 아니다")
    void keeps_user_without_withdrawal_request() {
        // given: 탈퇴를 신청한 적 없는 유저
        User active = saveUser();

        // when: 파기 배치 실행
        withdrawnUserDeletionService.deleteAll();

        // then: 그대로 유지된다
        User reloaded = reload(active.getId());
        assertThat(reloaded.getDeletedAt()).isNull();
        assertThat(reloaded.getEmail()).isNotNull();
    }

    @Test
    @DisplayName("탈퇴를 취소한 유저는 유예가 지났어도 파기되지 않는다")
    void keeps_user_who_cancelled_withdrawal() {
        // given: 탈퇴를 신청했다가 취소한 유저
        User cancelled = saveWithdrawalExpiredUser();
        cancelled.cancelWithdrawal();

        // when: 파기 배치 실행
        withdrawnUserDeletionService.deleteAll();

        // then: 조회 조건에서 빠져 그대로 유지된다
        User reloaded = reload(cancelled.getId());
        assertThat(reloaded.getDeletedAt()).isNull();
        assertThat(reloaded.getPhoneNumber()).isNotNull();
    }

    @Test
    @DisplayName("멱등성: 배치를 두 번 실행해도 파기 시각과 일반화된 생년월일이 바뀌지 않는다")
    void is_idempotent_across_runs() {
        // given: 유예가 만료된 유저를 한 번 파기한 상태
        User expired = saveWithdrawalExpiredUser();
        withdrawnUserDeletionService.deleteAll();

        Instant firstDeletedAt = reload(expired.getId()).getDeletedAt();

        // when: 같은 배치를 한 번 더 실행
        withdrawnUserDeletionService.deleteAll();

        // then: 이미 파기된 유저는 다시 처리되지 않는다
        User reloaded = reload(expired.getId());
        assertThat(reloaded.getDeletedAt()).isEqualTo(firstDeletedAt);
        assertThat(reloaded.getBirthDate()).isEqualTo("2000");
    }
}
