package com.nidus.twinly.common.websocket.interceptor;

import com.nidus.twinly.connection.domain.ConnectionTicketStatus;
import com.nidus.twinly.connection.domain.ConnectionType;
import com.nidus.twinly.connection.dto.result.ConnectionTicketResolveResult;
import com.nidus.twinly.connection.service.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class ConnectionTicketHandshakeInterceptor implements HandshakeInterceptor {

    private final ConnectionService connectionService;
    private final ConnectionType requiredConnectionType;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        UUID ticket = extractTicket(request);
        if (ticket == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        ConnectionTicketResolveResult result = connectionService.resolveTicket(ticket, requiredConnectionType);

        if (result.status() == ConnectionTicketStatus.SCOPE_MISMATCH) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        if (result.status() != ConnectionTicketStatus.AUTHORIZED) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put("userId", result.userId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
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
