package four_tential.potential.infra.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MdcFilterTest {

    private final MdcFilter mdcFilter = new MdcFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("traceId 헤더가 없으면 새 값을 생성하고 응답 헤더에 넣는다")
    void doFilterInternal_generatesTraceIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/members/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            traceIdInChain.set(MDC.get(MdcFilter.TRACE_ID));
            ((MockHttpServletResponse) res).setStatus(204);
        };

        mdcFilter.doFilterInternal(request, response, chain);

        String traceId = response.getHeader(MdcFilter.HEADER);
        assertThat(traceId).matches("[0-9a-f]{8}");
        assertThat(traceIdInChain.get()).isEqualTo(traceId);
        assertThat(MDC.get(MdcFilter.TRACE_ID)).isNull();
        assertThat(response.getStatus()).isEqualTo(204);
    }

    @Test
    @DisplayName("traceId 헤더가 있으면 같은 값을 MDC와 응답 헤더에 사용한다")
    void doFilterInternal_reusesIncomingTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payments");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        request.addHeader(MdcFilter.HEADER, "team-trace_01");

        FilterChain chain = (req, res) -> traceIdInChain.set(MDC.get(MdcFilter.TRACE_ID));

        mdcFilter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader(MdcFilter.HEADER)).isEqualTo("team-trace_01");
        assertThat(traceIdInChain.get()).isEqualTo("team-trace_01");
        assertThat(MDC.get(MdcFilter.TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("prometheus 경로도 traceId를 유지한 채 필터 체인을 통과한다")
    void doFilterInternal_allowsPrometheusUriWithoutBreakingTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> traceIdInChain.set(MDC.get(MdcFilter.TRACE_ID));

        mdcFilter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader(MdcFilter.HEADER)).isNotBlank();
        assertThat(traceIdInChain.get()).isEqualTo(response.getHeader(MdcFilter.HEADER));
        assertThat(MDC.get(MdcFilter.TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("필터 체인 예외가 발생해도 MDC는 정리된다")
    void doFilterInternal_clearsMdcWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/members/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> mdcFilter.doFilterInternal(request, response, chain))
                .isInstanceOf(ServletException.class)
                .hasMessage("boom");

        assertThat(MDC.get(MdcFilter.TRACE_ID)).isNull();
        assertThat(response.getHeader(MdcFilter.HEADER)).isNotBlank();
    }
}
