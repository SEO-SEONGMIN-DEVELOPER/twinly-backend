package com.nidus.twinly.purchase.writer;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.purchase.EarlySignupGrantProperties;
import com.nidus.twinly.purchase.entity.EarlySignupGrant;
import com.nidus.twinly.purchase.entity.EarlySignupGrantCounter;
import com.nidus.twinly.purchase.repository.EarlySignupGrantCounterRepository;
import com.nidus.twinly.purchase.repository.EarlySignupGrantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class EarlySignupGrantWriterUnitTest {

    private static final Long USER_ID = 1L;
    private static final Gender GENDER = Gender.MALE;
    private static final String DI_HASH = "di-hash";
    private static final Instant NOW = Instant.parse("2026-09-27T07:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-12-31T15:00:00Z");

    @Mock
    EarlySignupGrantCounterRepository earlySignupGrantCounterRepository;

    @Mock
    EarlySignupGrantRepository earlySignupGrantRepository;

    EarlySignupGrantWriter earlySignupGrantWriter;

    @BeforeEach
    void setUp() {
        earlySignupGrantWriter = new EarlySignupGrantWriter(
                earlySignupGrantCounterRepository, earlySignupGrantRepository, new EarlySignupGrantProperties(ENDS_AT));
    }

    @Test
    @DisplayName("자리가 남았고 처음 받는 본인이면 부여 대상 행을 만들고 카운터를 1 올린다")
    void assign_saves_grant_and_increases_counter() {
        // given: 가입자와 같은 성별로 지금까지 149명 배정, 이 DI 로 받은 적 없음
        EarlySignupGrantCounter counter = counter(GENDER, EarlySignupGrantCounter.LIMIT_PER_GENDER - 1);
        given(earlySignupGrantCounterRepository.findWithLockByGender(GENDER)).willReturn(Optional.of(counter));
        given(earlySignupGrantRepository.existsByDiHash(DI_HASH)).willReturn(false);

        // when: 배정
        earlySignupGrantWriter.assign(USER_ID, GENDER, DI_HASH, NOW);

        // then: 아직 부여 전(granted_at 없음)인 행이 저장되고 그 성별 카운터가 한도에 닿는다
        ArgumentCaptor<EarlySignupGrant> captor = ArgumentCaptor.forClass(EarlySignupGrant.class);
        then(earlySignupGrantRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getDiHash()).isEqualTo(DI_HASH);
        assertThat(captor.getValue().getGrantedAt()).isNull();
        assertThat(counter.getAssignedCount()).isEqualTo(EarlySignupGrantCounter.LIMIT_PER_GENDER);
    }

    @Test
    @DisplayName("만료 시각은 가입 시점과 무관하게 모두 같은 종료 시각(올해가 끝나는 순간)이다")
    void assign_expires_at_configured_end_regardless_of_signup_time() {
        // given: 9월에 가입한 사람과 올해 마지막 순간(한국 시간 12월 31일 23:59:59)에 가입한 사람
        given(earlySignupGrantCounterRepository.findWithLockByGender(GENDER)).willReturn(Optional.of(counter(GENDER, 0)));

        // when: 두 사람을 차례로 배정
        earlySignupGrantWriter.assign(USER_ID, GENDER, DI_HASH, NOW);
        earlySignupGrantWriter.assign(2L, GENDER, "other-di-hash", Instant.parse("2026-12-31T14:59:59Z"));

        // then: 둘 다 한국 시간 2027년 1월 1일 0시에 끝난다 (늦게 가입할수록 무료 기간이 짧아진다)
        ArgumentCaptor<EarlySignupGrant> captor = ArgumentCaptor.forClass(EarlySignupGrant.class);
        then(earlySignupGrantRepository).should(times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(EarlySignupGrant::getExpiresAt).containsOnly(ENDS_AT);
    }

    @Test
    @DisplayName("종료 시각이 지난 뒤 가입하면 자리를 배정하지 않고 카운터도 잠그지 않는다")
    void assign_skips_after_end() {
        // when: 한국 시간 2027년 1월 1일 0시 정각에 가입
        earlySignupGrantWriter.assign(USER_ID, GENDER, DI_HASH, ENDS_AT);

        // then: 이미 끝난 권한을 주느라 자리를 소모하지 않고, 캠페인이 끝난 뒤의 가입은 잠금도 기다리지 않는다
        then(earlySignupGrantCounterRepository).shouldHaveNoInteractions();
        then(earlySignupGrantRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("가입자 성별의 150명이 모두 배정됐으면 행을 만들지 않고 카운터도 그대로다")
    void assign_skips_when_full() {
        // given: 가입자 성별의 한도까지 배정 완료
        EarlySignupGrantCounter counter = counter(GENDER, EarlySignupGrantCounter.LIMIT_PER_GENDER);
        given(earlySignupGrantCounterRepository.findWithLockByGender(GENDER)).willReturn(Optional.of(counter));

        // when: 한도가 찬 상태에서 배정 시도
        earlySignupGrantWriter.assign(USER_ID, GENDER, DI_HASH, NOW);

        // then: 저장하지 않고 카운터도 한도에 머문다
        then(earlySignupGrantRepository).should(never()).save(any());
        assertThat(counter.getAssignedCount()).isEqualTo(EarlySignupGrantCounter.LIMIT_PER_GENDER);
    }

    @Test
    @DisplayName("가입자 성별의 카운터만 잠그고 센다: 남성 자리 상태와 무관하게 여성 가입자는 여성 카운터로 배정된다")
    void assign_uses_only_applicant_gender_counter() {
        // given: 여성 자리는 10명까지 배정된 상태
        EarlySignupGrantCounter female = counter(Gender.FEMALE, 10);
        given(earlySignupGrantCounterRepository.findWithLockByGender(Gender.FEMALE)).willReturn(Optional.of(female));

        // when: 여성 가입자 배정
        earlySignupGrantWriter.assign(USER_ID, Gender.FEMALE, DI_HASH, NOW);

        // then: 여성 카운터만 올라가고 남성 카운터는 잠그지도 않는다 (남녀 가입이 서로를 기다리지 않는다)
        then(earlySignupGrantRepository).should().save(any(EarlySignupGrant.class));
        assertThat(female.getAssignedCount()).isEqualTo(11);
        then(earlySignupGrantCounterRepository).should(never()).findWithLockByGender(Gender.MALE);
    }

    @Test
    @DisplayName("탈퇴 후 재가입처럼 같은 본인이 이미 받았으면 다시 배정하지 않는다")
    void assign_skips_when_di_already_granted() {
        // given: 자리는 남았지만 이 DI 로 받은 기록이 있음 (users.di_hash 는 탈퇴 때 지워진다)
        EarlySignupGrantCounter counter = counter(GENDER, 10);
        given(earlySignupGrantCounterRepository.findWithLockByGender(GENDER)).willReturn(Optional.of(counter));
        given(earlySignupGrantRepository.existsByDiHash(DI_HASH)).willReturn(true);

        // when: 같은 DI 로 배정 시도
        earlySignupGrantWriter.assign(USER_ID, GENDER, DI_HASH, NOW);

        // then: 자리를 소모하지 않는다
        then(earlySignupGrantRepository).should(never()).save(any());
        assertThat(counter.getAssignedCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("가입자 성별의 카운터 행이 없으면 예외를 던진다")
    void assign_throws_when_counter_missing() {
        // given: 마이그레이션이 넣어야 할 행이 없음
        given(earlySignupGrantCounterRepository.findWithLockByGender(GENDER)).willReturn(Optional.empty());

        // when & then: 잠글 행이 없으면 한도 보장이 깨지므로 조용히 넘어가지 않는다
        assertThatThrownBy(() -> earlySignupGrantWriter.assign(USER_ID, GENDER, DI_HASH, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("부여 완료 표시는 해당 행에 시각을 남긴다")
    void markGranted_updates_granted_at() {
        // when: 부여를 마친 행의 완료 표시
        earlySignupGrantWriter.markGranted(7L, NOW);

        // then: 해당 행 id 와 시각으로 조건부 갱신 쿼리에 위임한다
        then(earlySignupGrantRepository).should().markGranted(7L, NOW);
    }

    private EarlySignupGrantCounter counter(Gender gender, int assignedCount) {
        EarlySignupGrantCounter counter = BeanUtils.instantiateClass(EarlySignupGrantCounter.class);
        ReflectionTestUtils.setField(counter, "gender", gender);
        ReflectionTestUtils.setField(counter, "assignedCount", assignedCount);
        return counter;
    }
}
