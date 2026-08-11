package com.nidus.twinly.common.websocket.interceptor;

import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.common.logging.ErrorLog;
import com.nidus.twinly.connection.domain.ConnectionTicketStatus;
import com.nidus.twinly.connection.domain.ConnectionType;
import com.nidus.twinly.connection.dto.result.ConnectionTicketResolveResult;
import com.nidus.twinly.connection.service.ConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class ConnectionTicketHandshakeInterceptor implements HandshakeInterceptor {

    private static final String CONNECTION_TYPE = "connectionType";

    private final ConnectionService connectionService;
    private final ConnectionType requiredConnectionType;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        UUID ticket = extractTicket(request);
        if (ticket == null) {
            return reject(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "티켓이 없거나 형식이 올바르지 않습니다");
        }

        ConnectionTicketResolveResult result = connectionService.resolveTicket(ticket, requiredConnectionType);

        if (result.status() == ConnectionTicketStatus.SCOPE_MISMATCH) {
            return reject(response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "티켓 범위가 일치하지 않습니다");
        }

        if (result.status() != ConnectionTicketStatus.AUTHORIZED) {
            return reject(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "유효하지 않은 티켓입니다");
        }

        attributes.put("userId", result.userId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private boolean reject(ServerHttpResponse response, HttpStatus status, ErrorCode errorCode, String reason) {
        response.setStatusCode(status);

        ErrorLog.warn(log, errorCode.name(), null, null)
                .addKeyValue(CONNECTION_TYPE, requiredConnectionType.name())
                .log(reason);

        return false;
    }

    private UUID extractTicket(ServerHttpRequest request) {
        String query = request.getURI().getQuery();
        if (query == null) {
            return null;
        }

        String value = UriComponentsBuilder.newInstance()
                .query(query)
                .build()
                .getQueryParams()
                .getFirst("ticket");

        if (value == null) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
