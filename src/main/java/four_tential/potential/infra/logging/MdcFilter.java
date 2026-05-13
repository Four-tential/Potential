package four_tential.potential.infra.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String HEADER = "X-Trace-Id";

    // 5초마다 실행되는 Prometheus 요청 관련 Request start/end 로그는 보이지 않게 처리
    private static final Set<String> LOG_EXCLUDED_URIS = Set.of(
            "/actuator/prometheus"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String traceId = req.getHeader(HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
        }

        MDC.put(TRACE_ID, traceId);
        res.setHeader(HEADER, traceId);

        boolean shouldLogRequest = !LOG_EXCLUDED_URIS.contains(req.getRequestURI());

        if (shouldLogRequest) {
            log.info("Request start method={} uri={}", req.getMethod(), req.getRequestURI());
        }

        try {
            chain.doFilter(req, res);
        } finally {
            if (shouldLogRequest) {
                log.info("Request end method={} uri={} status={}", req.getMethod(), req.getRequestURI(), res.getStatus());
            }
            MDC.clear();
        }
    }
}
