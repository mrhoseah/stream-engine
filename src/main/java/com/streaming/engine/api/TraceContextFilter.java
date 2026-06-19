package com.streaming.engine.api;

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

/**
 * Propagates W3C traceparent from recastly (and other callers) into MDC and Kafka analytics events.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
public class TraceContextFilter extends OncePerRequestFilter {

    public static final String HEADER = "traceparent";
    public static final String MDC_KEY = "traceparent";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        if (incoming != null && !incoming.isBlank()) {
            String traceparent = incoming.trim();
            MDC.put(MDC_KEY, traceparent);
            response.setHeader(HEADER, traceparent);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
