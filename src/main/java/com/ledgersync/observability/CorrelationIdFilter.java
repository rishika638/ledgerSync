package com.ledgersync.observability;

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
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Propagates correlation identifiers and W3C TraceContext headers (traceparent)
 * across incoming HTTP boundaries, logging MDC, and downstream responses.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String MDC_CORRELATION_KEY = "correlationId";
    public static final String MDC_TRACE_ID_KEY = "traceId";
    public static final String MDC_SPAN_ID_KEY = "spanId";

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        String traceparent = request.getHeader(TRACEPARENT_HEADER);

        String traceId;
        String spanId;

        if (traceparent != null && traceparent.startsWith("00-") && traceparent.split("-").length >= 4) {
            String[] parts = traceparent.split("-");
            traceId = parts[1];
            spanId = parts[2];
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = traceId;
            }
        } else {
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            byte[] traceBytes = new byte[16];
            byte[] spanBytes = new byte[8];
            RANDOM.nextBytes(traceBytes);
            RANDOM.nextBytes(spanBytes);
            traceId = HexFormat.of().formatHex(traceBytes);
            spanId = HexFormat.of().formatHex(spanBytes);
            traceparent = "00-" + traceId + "-" + spanId + "-01";
        }

        MDC.put(MDC_CORRELATION_KEY, correlationId);
        MDC.put(MDC_TRACE_ID_KEY, traceId);
        MDC.put(MDC_SPAN_ID_KEY, spanId);

        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        response.setHeader(TRACEPARENT_HEADER, traceparent);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_KEY);
            MDC.remove(MDC_TRACE_ID_KEY);
            MDC.remove(MDC_SPAN_ID_KEY);
        }
    }
}
