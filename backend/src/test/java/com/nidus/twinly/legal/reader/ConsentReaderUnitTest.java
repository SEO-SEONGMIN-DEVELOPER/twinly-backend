package com.nidus.twinly.legal.reader;

import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.legal.entity.Agreement;
import com.nidus.twinly.legal.repository.AgreementRepository;
import com.nidus.twinly.legal.service.PolicyCatalog;
import com.nidus.twinly.legal.service.PolicyCatalog.PolicyKey;
import com.nidus.twinly.support.TestPolicySummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ConsentReaderUnitTest {

    private static final Long USER_ID = 1L;
    private static final String DISCLOSURE = "thirdPartyRealIdentityDisclosure";

    @Mock
    PolicyCatalog policyCatalog;

    @Mock
    AgreementRepository agreementRepository;

    @InjectMocks
    ConsentReader consentReader;

    @Test
    @DisplayName("필수 약관 최신 버전에 모두 동의했으면 true 를 반환한다")
    void hasAgreedAllRequired_true_when_all_latest_agreed() {
        // given: 필수 최신 버전 20번에 동의한 상태
        given(policyCatalog.loadRequiredPolicyIds(PolicyKind.PARALLEL_ENTRY)).willReturn(Set.of(20L));
        given(agreementRepository.findAllByUserIdAndRevokedAtIsNull(USER_ID)).willReturn(List.of(Agreement.create(USER_ID, 20L, Instant.now())));

        // when & then
        assertThat(consentReader.hasAgreedAllRequired(USER_ID, PolicyKind.PARALLEL_ENTRY)).isTrue();
    }

    @Test
    @DisplayName("구버전에만 동의했으면 false 를 반환한다")
    void hasAgreedAllRequired_false_when_only_old_version_agreed() {
        // given: 필수 최신 버전은 20번인데 구버전 10번에만 동의한 상태
        given(policyCatalog.loadRequiredPolicyIds(PolicyKind.PARALLEL_ENTRY)).willReturn(Set.of(20L));
        given(agreementRepository.findAllByUserIdAndRevokedAtIsNull(USER_ID)).willReturn(List.of(Agreement.create(USER_ID, 10L, Instant.now())));

        // when & then: 약관이 개정되면 다시 동의해야 입장할 수 있다
        assertThat(consentReader.hasAgreedAllRequired(USER_ID, PolicyKind.PARALLEL_ENTRY)).isFalse();
    }

    @Test
    @DisplayName("필수 약관이 하나도 없으면 동의 기록이 없어도 true 를 반환한다")
    void hasAgreedAllRequired_true_when_no_required_policy() {
        // given: 평행우주 입장 필수 약관이 없음
        given(policyCatalog.loadRequiredPolicyIds(PolicyKind.PARALLEL_ENTRY)).willReturn(Set.of());
        given(agreementRepository.findAllByUserIdAndRevokedAtIsNull(USER_ID)).willReturn(List.of());

        // when & then
        assertThat(consentReader.hasAgreedAllRequired(USER_ID, PolicyKind.PARALLEL_ENTRY)).isTrue();
    }

    @Test
    @DisplayName("여러 유저 중 필수 약관 최신 버전에 모두 동의한 유저만 순서대로 남긴다")
    void filterAgreedAllRequired_keeps_only_fully_agreed_users() {
        // given: 필수 약관 20·21번. 1번은 둘 다, 2번은 20번만, 3번은 동의 없음
        given(policyCatalog.loadRequiredPolicyIds(PolicyKind.PARALLEL_ENTRY)).willReturn(Set.of(20L, 21L));
        given(agreementRepository.findAllByUserIdInAndRevokedAtIsNull(List.of(1L, 2L, 3L))).willReturn(List.of(
                Agreement.create(1L, 20L, Instant.now()),
                Agreement.create(1L, 21L, Instant.now()),
                Agreement.create(2L, 20L, Instant.now())));

        // when & then
        assertThat(consentReader.filterAgreedAllRequired(List.of(1L, 2L, 3L), PolicyKind.PARALLEL_ENTRY)).containsExactly(1L);
    }

    @Test
    @DisplayName("대상 유저가 없으면 조회 없이 빈 목록을 반환한다")
    void filterAgreedAllRequired_empty_users() {
        // when & then: 빈 IN 조회를 만들지 않는다
        assertThat(consentReader.filterAgreedAllRequired(List.of(), PolicyKind.PARALLEL_ENTRY)).isEmpty();
    }

    @Test
    @DisplayName("기준 버전과 그보다 늦게 시행된 버전에 동의한 유저만 남기고, 버전 문자열이 아니라 시행 순서로 비교한다")
    void filterAgreedAtLeast_compares_by_effective_order() {
        // given: 1.1(10번)·1.2(20번)·1.10(30번) 순서로 시행. 1번은 1.1, 2번은 1.2, 3번은 1.10, 4번은 동의 없음
        Instant base = Instant.parse("2026-10-01T00:00:00Z");
        given(policyCatalog.loadByKey(List.of(DISCLOSURE))).willReturn(Map.of(
                new PolicyKey(DISCLOSURE, "1.1"), new TestPolicySummary(10L, 5L, "1.1", true, base),
                new PolicyKey(DISCLOSURE, "1.2"), new TestPolicySummary(20L, 5L, "1.2", true, base.plusSeconds(60)),
                new PolicyKey(DISCLOSURE, "1.10"), new TestPolicySummary(30L, 5L, "1.10", true, base.plusSeconds(120))));
        given(agreementRepository.findAllByUserIdInAndRevokedAtIsNull(List.of(1L, 2L, 3L, 4L))).willReturn(List.of(
                Agreement.create(1L, 10L, Instant.now()),
                Agreement.create(2L, 20L, Instant.now()),
                Agreement.create(3L, 30L, Instant.now())));

        // when & then: 문자열로는 "1.10" < "1.2" 지만 시행 순서상 1.10 동의자도 포함된다
        assertThat(consentReader.filterAgreedAtLeast(List.of(1L, 2L, 3L, 4L), DISCLOSURE, "1.2")).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @DisplayName("기준 버전이 아직 시행 전이면 동의 기록을 보지 않고 빈 집합을 반환한다")
    void filterAgreedAtLeast_empty_before_minimum_version_is_effective() {
        // given: 시행 중인 버전은 1.1 뿐
        given(policyCatalog.loadByKey(List.of(DISCLOSURE))).willReturn(Map.of(
                new PolicyKey(DISCLOSURE, "1.1"), new TestPolicySummary(10L, 5L, "1.1", true, Instant.now())));

        // when & then: 아무도 기준을 넘지 못하고 동의 조회도 하지 않는다
        assertThat(consentReader.filterAgreedAtLeast(List.of(1L), DISCLOSURE, "1.2")).isEmpty();
        then(agreementRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("대상 유저가 없으면 조회 없이 빈 집합을 반환한다")
    void filterAgreedAtLeast_empty_users() {
        // when & then
        assertThat(consentReader.filterAgreedAtLeast(List.of(), DISCLOSURE, "1.2")).isEmpty();
        then(policyCatalog).shouldHaveNoInteractions();
    }
}
