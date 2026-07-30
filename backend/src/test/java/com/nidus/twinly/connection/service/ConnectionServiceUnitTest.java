package com.nidus.twinly.connection.service;

import com.nidus.twinly.connection.domain.ConnectionTicketStatus;
import com.nidus.twinly.connection.domain.ConnectionType;
import com.nidus.twinly.connection.dto.command.ConnectionTokenCommand;
import com.nidus.twinly.connection.dto.result.ConnectionTicketResolveResult;
import com.nidus.twinly.connection.dto.result.ConnectionTokenResult;
import com.nidus.twinly.connection.entity.ConnectionTicket;
import com.nidus.twinly.connection.repository.ConnectionTicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ConnectionServiceUnitTest {

    @Mock
    ConnectionTicketRepository connectionTicketRepository;

    @InjectMocks
    ConnectionService connectionService;

    @Test
    @DisplayName("연결 토큰 발급 시 userId·connectionType으로 티켓을 저장하고 저장한 값을 그대로 반환한다")
    void token_saves_ticket_and_returns_saved_values() {
        // given: WS 연결 토큰 발급 커맨드
        ConnectionTokenCommand command = new ConnectionTokenCommand(ConnectionType.WS);

        // when: 연결 토큰 발급
        ConnectionTokenResult result = connectionService.token(1L, command);

        // then: userId·connectionType으로 티켓 저장 위임 + 저장한 티켓 값을 그대로 응답
        ArgumentCaptor<ConnectionTicket> captor = ArgumentCaptor.forClass(ConnectionTicket.class);
        then(connectionTicketRepository).should().save(captor.capture());

        ConnectionTicket saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getConnectionType()).isEqualTo(ConnectionType.WS);
        assertThat(saved.getTicket()).isNotNull();
        assertThat(saved.getUsedAt()).isNull();

        assertThat(result.ticket()).isEqualTo(saved.getTicket());
        assertThat(result.connectionType()).isEqualTo(ConnectionType.WS);
        assertThat(result.expiresAt()).isEqualTo(saved.getExpiresAt());
    }

    @Test
    @DisplayName("발급된 티켓의 만료 시각은 발급 시점으로부터 60초 뒤로 설정된다")
    void token_sets_expires_at_60_seconds_later() {
        // given: 발급 직전 시각을 기록
        Instant before = Instant.now();

        // when: SSE 연결 토큰 발급
        ConnectionTokenResult result = connectionService.token(1L, new ConnectionTokenCommand(ConnectionType.SSE));

        // then: 만료 시각이 발급 시점 + 60초 범위 안에 위치
        Instant after = Instant.now();
        assertThat(result.expiresAt()).isBetween(before.plusSeconds(60), after.plusSeconds(60));
    }

    @Test
    @DisplayName("연결 토큰을 두 번 발급하면 서로 다른 티켓이 만들어진다")
    void token_issues_unique_ticket_per_call() {
        // when: 같은 유저가 연속으로 두 번 발급
        ConnectionTokenResult first = connectionService.token(1L, new ConnectionTokenCommand(ConnectionType.WS));
        ConnectionTokenResult second = connectionService.token(1L, new ConnectionTokenCommand(ConnectionType.WS));

        // then: 티켓 값이 서로 다름 (추측 불가한 1회용 티켓)
        assertThat(first.ticket()).isNotEqualTo(second.ticket());
    }

    @Test
    @DisplayName("존재하지 않는 티켓이면 INVALID를 반환하고 소비를 시도하지 않는다")
    void resolveTicket_not_found_returns_invalid() {
        // given: 해당 티켓이 DB에 없음
        UUID ticket = UUID.randomUUID();
        given(connectionTicketRepository.findByTicket(ticket)).willReturn(Optional.empty());

        // when: 티켓 검증
        ConnectionTicketResolveResult result = connectionService.resolveTicket(ticket, ConnectionType.WS);

        // then: INVALID 반환 + userId 없음 + 소비 시도 안 함
        assertThat(result.status()).isEqualTo(ConnectionTicketStatus.INVALID);
        assertThat(result.userId()).isNull();
        then(connectionTicketRepository).should(never()).consume(any());
    }

    @Test
    @DisplayName("티켓의 connectionType이 요구 타입과 달라도 티켓은 소비하고 SCOPE_MISMATCH를 반환한다")
    void resolveTicket_scope_mismatch_consumes_ticket() {
        // given: SSE용으로 발급된 티켓
        UUID ticket = UUID.randomUUID();
        given(connectionTicketRepository.findByTicket(ticket)).willReturn(Optional.of(ticket(ticket, 7L, ConnectionType.SSE)));
        given(connectionTicketRepository.consume(ticket)).willReturn(1);

        // when: WS 연결에 사용하려 시도
        ConnectionTicketResolveResult result = connectionService.resolveTicket(ticket, ConnectionType.WS);

        // then: SCOPE_MISMATCH를 반환하되 티켓은 소비돼 같은 티켓으로 재시도할 수 없다
        assertThat(result.status()).isEqualTo(ConnectionTicketStatus.SCOPE_MISMATCH);
        assertThat(result.userId()).isNull();
        then(connectionTicketRepository).should().consume(ticket);
    }

    @Test
    @DisplayName("이미 사용되었거나 만료되어 소비에 실패하면 INVALID를 반환한다")
    void resolveTicket_consume_fails_returns_invalid() {
        // given: 티켓은 조회되지만 소비 쿼리가 0행을 갱신 (이미 사용됨 또는 만료됨)
        UUID ticket = UUID.randomUUID();
        given(connectionTicketRepository.findByTicket(ticket)).willReturn(Optional.of(ticket(ticket, 7L, ConnectionType.WS)));
        given(connectionTicketRepository.consume(ticket)).willReturn(0);

        // when: 티켓 검증
        ConnectionTicketResolveResult result = connectionService.resolveTicket(ticket, ConnectionType.WS);

        // then: INVALID 반환 + userId 없음
        assertThat(result.status()).isEqualTo(ConnectionTicketStatus.INVALID);
        assertThat(result.userId()).isNull();
    }

    @Test
    @DisplayName("타입이 일치하고 소비에 성공하면 AUTHORIZED와 티켓 소유자 id를 반환한다")
    void resolveTicket_success_returns_authorized() {
        // given: WS용 유효 티켓이 조회되고 소비 쿼리가 1행을 갱신
        UUID ticket = UUID.randomUUID();
        given(connectionTicketRepository.findByTicket(ticket)).willReturn(Optional.of(ticket(ticket, 7L, ConnectionType.WS)));
        given(connectionTicketRepository.consume(ticket)).willReturn(1);

        // when: 티켓 검증
        ConnectionTicketResolveResult result = connectionService.resolveTicket(ticket, ConnectionType.WS);

        // then: AUTHORIZED 반환 + 티켓 소유자 id 전달
        assertThat(result.status()).isEqualTo(ConnectionTicketStatus.AUTHORIZED);
        assertThat(result.userId()).isEqualTo(7L);
    }

    private ConnectionTicket ticket(UUID ticket, Long userId, ConnectionType connectionType) {
        ConnectionTicket connectionTicket = ConnectionTicket.create(userId, connectionType, Instant.now().plusSeconds(60));
        ReflectionTestUtils.setField(connectionTicket, "ticket", ticket);
        return connectionTicket;
    }
}
