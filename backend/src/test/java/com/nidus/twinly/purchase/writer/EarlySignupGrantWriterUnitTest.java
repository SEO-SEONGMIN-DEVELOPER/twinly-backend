package com.nidus.twinly.purchase.writer;

import com.nidus.twinly.purchase.entity.EarlySignupGrant;
import com.nidus.twinly.purchase.entity.EarlySignupGrantCounter;
import com.nidus.twinly.purchase.repository.EarlySignupGrantCounterRepository;
import com.nidus.twinly.purchase.repository.EarlySignupGrantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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

@ExtendWith(MockitoExtension.class)
class EarlySignupGrantWriterUnitTest {

    private static final Long USER_ID = 1L;
    private static final String DI_HASH = "di-hash";
    private static final Instant NOW = Instant.parse("2026-09-27T07:00:00Z");

    @Mock
    EarlySignupGrantCounterRepository earlySignupGrantCounterRepository;

    @Mock
    EarlySignupGrantRepository earlySignupGrantRepository;

    @InjectMocks
    EarlySignupGrantWriter earlySignupGrantWriter;

    @Test
    @DisplayName("자리가 남았고 처음 받는 본인이면 부여 대상 행을 만들고 카운터를 1 올린다")
    void assign_saves_grant_and_increases_counter() {
        // given: 지금까지 299명 배정, 이 DI 로 받은 적 없음
        EarlySignupGrantCounter counter = counter(EarlySignupGrantCounter.LIMIT - 1);
        given(earlySignupGrantCounterRepository.findWithLockById(EarlySignupGrantCounter.SINGLETON_ID)).willReturn(Optional.of(counter));
        given(earlySignupGrantRepository.existsByDiHash(DI_HASH)).willReturn(false);

        // when: 배정
        earlySignupGrantWriter.assign(USER_ID, DI_HASH, NOW);

        // then: 아직 부여 전(granted_at 없음)인 행이 저장되고 카운터가 한도에 닿는다
        ArgumentCaptor<EarlySignupGrant> captor = ArgumentCaptor.forClass(EarlySignupGrant.class);
        then(earlySignupGrantRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getDiHash()).isEqualTo(DI_HASH);
        assertThat(captor.getValue().getGrantedAt()).isNull();
        assertThat(counter.getAssignedCount()).isEqualTo(EarlySignupGrantCounter.LIMIT);
    }

    @Test
    @DisplayName("만료 시각은 배정 시점에서 두 달 뒤, 같은 시각이다")
    void assign_expires_two_months_later() {
        // given: 한국 시간 9월 27일 16시에 가입
        given(earlySignupGrantCounterRepository.findWithLockById(EarlySignupGrantCounter.SINGLETON_ID)).willReturn(Optional.of(counter(0)));

        // when: 첫 자리로 배정
        earlySignupGrantWriter.assign(USER_ID, DI_HASH, NOW);

        // then: 11월 27일 16시에 끝난다
        ArgumentCaptor<EarlySignupGrant> captor = ArgumentCaptor.forClass(EarlySignupGrant.class);
        then(earlySignupGrantRepository).should().save(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(Instant.parse("2026-11-27T07:00:00Z"));
    }

    @Test
    @DisplayName("두 달은 한국 달력으로 센다: 한국 날짜로 12월 31일 가입이면 2월 말일에 끝난다")
    void assign_counts_months_on_kst_calendar() {
        // given: UTC 로는 12월 30일 15시지만 한국 시간으로는 12월 31일 0시에 가입
        Instant assignedAt = Instant.parse("2026-12-30T15:00:00Z");
        given(earlySignupGrantCounterRepository.findWithLockById(EarlySignupGrantCounter.SINGLETON_ID)).willReturn(Optional.of(counter(0)));

        // when: 한국 날짜 기준 연말에 배정
        earlySignupGrantWriter.assign(USER_ID, DI_HASH, assignedAt);

        // then: 한국 시간 2월 28일 0시에 끝난다 (UTC 달력으로 세면 12월 30일 기준이라 하루 늦은 2월 28일 15시 UTC 가 된다)
        ArgumentCaptor<EarlySignupGrant> captor = ArgumentCaptor.forClass(EarlySignupGrant.class);
        then(earlySignupGrantRepository).should().save(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(Instant.parse("2027-02-27T15:00:00Z"));
    }

    @Test
    @DisplayName("만료 시각은 RevenueCat 이 받는 밀리초 단위로 자른다: 첫 부여와 DB 에서 읽은 재시도가 같은 값을 보낸다")
    void assign_truncates_expiry_to_millis() {
        // given: Linux 의 Instant.now() 처럼 나노초까지 있는 배정 시각 (DB 는 마이크로초까지만 저장한다)
        Instant assignedAt = Instant.parse("2026-09-27T07:00:00.327706361Z");
        given(earlySignupGrantCounterRepository.findWithLockById(EarlySignupGrantCounter.SINGLETON_ID)).willReturn(Optional.of(counter(0)));

        // when: 배정
        earlySignupGrantWriter.assign(USER_ID, DI_HASH, assignedAt);

        // then: 밀리초 아래는 버려져, 메모리·DB·RevenueCat end_time_ms 가 모두 같은 시각이 된다
        ArgumentCaptor<EarlySignupGrant> captor = ArgumentCaptor.forClass(EarlySignupGrant.class);
        then(earlySignupGrantRepository).should().save(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(Instant.parse("2026-11-27T07:00:00.327Z"));
    }

    @Test
    @DisplayName("300명이 모두 배정됐으면 행을 만들지 않고 카운터도 그대로다")
    void assign_skips_when_full() {
        // given: 한도까지 배정 완료
        EarlySignupGrantCounter counter = counter(EarlySignupGrantCounter.LIMIT);
        given(earlySignupGrantCounterRepository.findWithLockById(EarlySignupGrantCounter.SINGLETON_ID)).willReturn(Optional.of(counter));

        // when: 한도가 찬 상태에서 배정 시도
        earlySignupGrantWriter.assign(USER_ID, DI_HASH, NOW);

        // then: 저장하지 않고 카운터도 한도에 머문다
        then(earlySignupGrantRepository).should(never()).save(any());
        assertThat(counter.getAssignedCount()).isEqualTo(EarlySignupGrantCounter.LIMIT);
    }

    @Test
    @DisplayName("탈퇴 후 재가입처럼 같은 본인이 이미 받았으면 다시 배정하지 않는다")
    void assign_skips_when_di_already_granted() {
        // given: 자리는 남았지만 이 DI 로 받은 기록이 있음 (users.di_hash 는 탈퇴 때 지워진다)
        EarlySignupGrantCounter counter = counter(10);
        given(earlySignupGrantCounterRepository.findWithLockById(EarlySignupGrantCounter.SINGLETON_ID)).willReturn(Optional.of(counter));
        given(earlySignupGrantRepository.existsByDiHash(DI_HASH)).willReturn(true);

        // when: 같은 DI 로 배정 시도
        earlySignupGrantWriter.assign(USER_ID, DI_HASH, NOW);

        // then: 자리를 소모하지 않는다
        then(earlySignupGrantRepository).should(never()).save(any());
        assertThat(counter.getAssignedCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("카운터 행이 없으면 예외를 던진다")
    void assign_throws_when_counter_missing() {
        // given: 마이그레이션이 넣어야 할 행이 없음
        given(earlySignupGrantCounterRepository.findWithLockById(EarlySignupGrantCounter.SINGLETON_ID)).willReturn(Optional.empty());

        // when & then: 잠글 행이 없으면 한도 보장이 깨지므로 조용히 넘어가지 않는다
        assertThatThrownBy(() -> earlySignupGrantWriter.assign(USER_ID, DI_HASH, NOW))
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

    private EarlySignupGrantCounter counter(int assignedCount) {
        EarlySignupGrantCounter counter = BeanUtils.instantiateClass(EarlySignupGrantCounter.class);
        ReflectionTestUtils.setField(counter, "id", EarlySignupGrantCounter.SINGLETON_ID);
        ReflectionTestUtils.setField(counter, "assignedCount", assignedCount);
        return counter;
    }
}
