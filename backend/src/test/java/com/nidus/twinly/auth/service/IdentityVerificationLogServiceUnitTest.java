package com.nidus.twinly.auth.service;

import com.nidus.twinly.auth.domain.IdentityVerificationResult;
import com.nidus.twinly.auth.entity.IdentityVerificationLog;
import com.nidus.twinly.auth.repository.IdentityVerificationLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class IdentityVerificationLogServiceUnitTest {

    private static final Long ANON_SESSION_ID = 1L;
    private static final String REQUEST_NO = "TWINLY-11111111-2222-3333-4444-555555555555";
    private static final String TRANSACTION_ID = "UzE0MUQyNkFDOEQ3NzYyMDIwMjUxMTEzMTAwMjM3MzM4OTQ5QUMwMkU";
    private static final String DI_HASH = "hash:di-value";

    @Mock
    IdentityVerificationLogRepository identityVerificationLogRepository;

    @InjectMocks
    IdentityVerificationLogService identityVerificationLogService;

    @Test
    @DisplayName("발급 기록: 세션·request_no·transaction_id 를 담아 ISSUED 상태로 저장하고 결과 관련 값은 비워둔다")
    void issued_saves_issued_row() {
        // when
        identityVerificationLogService.issued(ANON_SESSION_ID, REQUEST_NO, TRANSACTION_ID);

        // then
        ArgumentCaptor<IdentityVerificationLog> captor = ArgumentCaptor.forClass(IdentityVerificationLog.class);
        then(identityVerificationLogRepository).should().save(captor.capture());

        IdentityVerificationLog saved = captor.getValue();
        assertThat(saved.getAnonSessionId()).isEqualTo(ANON_SESSION_ID);
        assertThat(saved.getRequestNo()).isEqualTo(REQUEST_NO);
        assertThat(saved.getTransactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(saved.getResult()).isEqualTo(IdentityVerificationResult.ISSUED);
        assertThat(saved.getDiHash()).isNull();
        assertThat(saved.getResultedAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("결과 기록: 같은 transaction_id 의 발급 기록이 있으면 새 행을 만들지 않고 결과·di_hash·시각을 채운다")
    void completed_updates_existing_row() {
        // given
        IdentityVerificationLog issued = IdentityVerificationLog.issue(ANON_SESSION_ID, REQUEST_NO, TRANSACTION_ID);
        given(identityVerificationLogRepository.findFirstByTransactionIdOrderByIdDesc(TRANSACTION_ID))
                .willReturn(Optional.of(issued));

        // when
        identityVerificationLogService.completed(
                ANON_SESSION_ID, REQUEST_NO, TRANSACTION_ID, IdentityVerificationResult.AGE_NOT_ALLOWED, DI_HASH);

        // then
        then(identityVerificationLogRepository).should(never()).save(any());
        assertThat(issued.getResult()).isEqualTo(IdentityVerificationResult.AGE_NOT_ALLOWED);
        assertThat(issued.getDiHash()).isEqualTo(DI_HASH);
        assertThat(issued.getResultedAt()).isNotNull();
    }

    @Test
    @DisplayName("결과 기록: 발급 기록이 없으면 과금 건을 잃지 않도록 새 행을 만들어 결과까지 채운다")
    void completed_creates_row_when_issued_log_is_missing() {
        // given
        given(identityVerificationLogRepository.findFirstByTransactionIdOrderByIdDesc(TRANSACTION_ID))
                .willReturn(Optional.empty());
        given(identityVerificationLogRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        // when
        identityVerificationLogService.completed(
                ANON_SESSION_ID, REQUEST_NO, TRANSACTION_ID, IdentityVerificationResult.VERIFIED, DI_HASH);

        // then
        ArgumentCaptor<IdentityVerificationLog> captor = ArgumentCaptor.forClass(IdentityVerificationLog.class);
        then(identityVerificationLogRepository).should().save(captor.capture());

        IdentityVerificationLog created = captor.getValue();
        assertThat(created.getAnonSessionId()).isEqualTo(ANON_SESSION_ID);
        assertThat(created.getRequestNo()).isEqualTo(REQUEST_NO);
        assertThat(created.getTransactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(created.getResult()).isEqualTo(IdentityVerificationResult.VERIFIED);
        assertThat(created.getDiHash()).isEqualTo(DI_HASH);
        assertThat(created.getResultedAt()).isNotNull();
    }
}
