package com.nidus.twinly.purchase.writer;

import com.nidus.twinly.purchase.client.RevenueCatEntitlement;
import com.nidus.twinly.purchase.entity.UserEntitlement;
import com.nidus.twinly.purchase.repository.UserEntitlementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserEntitlementWriterUnitTest {

    private static final Long USER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-30T00:00:00Z");

    @Mock
    UserEntitlementRepository userEntitlementRepository;

    @InjectMocks
    UserEntitlementWriter userEntitlementWriter;

    @Test
    @DisplayName("저장된 권한이 없으면 받아온 권한을 새로 저장한다")
    void replaceAll_saves_new_entitlements() {
        // given: 저장된 권한이 없음
        given(userEntitlementRepository.findAllByUserId(USER_ID)).willReturn(List.of());

        // when: premium 권한을 받아 교체
        userEntitlementWriter.replaceAll(USER_ID, List.of(new RevenueCatEntitlement("premium", EXPIRES_AT)), NOW);

        // then: userId·권한명·만료·동기화 시각으로 새 행 저장
        ArgumentCaptor<UserEntitlement> captor = ArgumentCaptor.forClass(UserEntitlement.class);
        then(userEntitlementRepository).should().save(captor.capture());

        UserEntitlement saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getEntitlement()).isEqualTo("premium");
        assertThat(saved.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(saved.getSyncedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("이미 있는 권한은 새로 저장하지 않고 만료·동기화 시각만 갱신한다")
    void replaceAll_updates_existing_entitlement() {
        // given: 지난달 만료로 저장된 premium 권한
        Instant oldExpiresAt = Instant.parse("2026-08-30T00:00:00Z");
        UserEntitlement stored = UserEntitlement.create(USER_ID, "premium", oldExpiresAt, NOW.minusSeconds(3600));
        given(userEntitlementRepository.findAllByUserId(USER_ID)).willReturn(List.of(stored));

        // when: 만료가 미뤄진 premium 권한을 받아 교체
        userEntitlementWriter.replaceAll(USER_ID, List.of(new RevenueCatEntitlement("premium", EXPIRES_AT)), NOW);

        // then: 저장은 일어나지 않고 기존 행의 값만 갱신됨
        then(userEntitlementRepository).should(never()).save(any());
        assertThat(stored.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(stored.getSyncedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("받아온 목록에 없는 권한은 삭제한다")
    void replaceAll_deletes_missing_entitlement() {
        // given: premium·noAds 가 저장돼 있음
        UserEntitlement premium = UserEntitlement.create(USER_ID, "premium", EXPIRES_AT, NOW.minusSeconds(3600));
        UserEntitlement noAds = UserEntitlement.create(USER_ID, "noAds", EXPIRES_AT, NOW.minusSeconds(3600));
        given(userEntitlementRepository.findAllByUserId(USER_ID)).willReturn(List.of(premium, noAds));

        // when: premium 만 담긴 목록으로 교체 (noAds 는 환불되어 빠짐)
        userEntitlementWriter.replaceAll(USER_ID, List.of(new RevenueCatEntitlement("premium", EXPIRES_AT)), NOW);

        // then: 목록에서 빠진 noAds 만 삭제됨
        ArgumentCaptor<Collection<UserEntitlement>> captor = ArgumentCaptor.forClass(Collection.class);
        then(userEntitlementRepository).should().deleteAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(noAds);
    }

    @Test
    @DisplayName("빈 목록을 받으면 저장된 권한을 모두 삭제한다")
    void replaceAll_with_empty_list_deletes_all() {
        // given: premium 이 저장돼 있음
        UserEntitlement premium = UserEntitlement.create(USER_ID, "premium", EXPIRES_AT, NOW.minusSeconds(3600));
        given(userEntitlementRepository.findAllByUserId(USER_ID)).willReturn(List.of(premium));

        // when: 권한이 하나도 없는 목록으로 교체
        userEntitlementWriter.replaceAll(USER_ID, List.of(), NOW);

        // then: 저장된 권한이 전부 삭제됨
        ArgumentCaptor<Collection<UserEntitlement>> captor = ArgumentCaptor.forClass(Collection.class);
        then(userEntitlementRepository).should().deleteAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(premium);
    }

    @Test
    @DisplayName("더 최신 동기화가 이미 반영돼 있으면 아무것도 바꾸지 않는다")
    void replaceAll_with_stale_sync_is_noop() {
        // given: 우리 조회 시작 시각보다 나중에 동기화된 행이 존재 (조회가 늦게 끝난 상황)
        UserEntitlement stored = UserEntitlement.create(USER_ID, "premium", EXPIRES_AT, NOW.plusSeconds(10));
        given(userEntitlementRepository.findAllByUserId(USER_ID)).willReturn(List.of(stored));

        // when: 낡은 조회 결과로 교체 시도
        userEntitlementWriter.replaceAll(USER_ID, List.of(), NOW);

        // then: 저장도 삭제도 일어나지 않고 기존 값이 유지됨
        then(userEntitlementRepository).should(never()).save(any());
        then(userEntitlementRepository).should(never()).deleteAll(any());
        assertThat(stored.getExpiresAt()).isEqualTo(EXPIRES_AT);
    }
}
