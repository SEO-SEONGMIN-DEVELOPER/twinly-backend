package com.nidus.twinly.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String HTTP_METHOD = "httpMethod";
    private static final String HTTP_PATH = "httpPath";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = TraceContext.newTraceId();
        MDC.put(TraceContext.TRACE_ID, traceId);
        MDC.put(HTTP_METHOD, request.getMethod());
        MDC.put(HTTP_PATH, request.getRequestURI());
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceContext.TRACE_ID);
            MDC.remove(HTTP_METHOD);
            MDC.remove(HTTP_PATH);
        }
    }
}
