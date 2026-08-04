package com.nidus.twinly.common.security;

import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.common.web.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String ERROR_CODE_ATTRIBUTE = SecurityErrorResponder.class.getName() + ".ERROR_CODE";

    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        Object attribute = request.getAttribute(ERROR_CODE_ATTRIBUTE);
        ErrorCode errorCode = attribute instanceof ErrorCode resolved ? resolved : ErrorCode.UNAUTHORIZED;

        write(response, errorCode);
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        write(response, ErrorCode.FORBIDDEN);
    }

    private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(ErrorResponse.of(errorCode)));
    }
}
