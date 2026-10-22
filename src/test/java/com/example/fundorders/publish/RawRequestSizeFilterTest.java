package com.example.fundorders.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RawRequestSizeFilterTest {
    private final RawRequestSizeFilter filter = new RawRequestSizeFilter();

    @Test
    void rejectsUnknownLengthBodyBeforeDispatchAfterReadingOnlyLimitPlusOne() throws Exception {
        var request = new UnknownLengthRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/fund-order-events/raw");
        request.setContent("a".repeat(65_537).getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void passesOriginalBytesToDispatcherWithinLimit() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/fund-order-events/raw");
        var body = "évent".getBytes(StandardCharsets.UTF_8);
        request.setContent(body);
        var response = new MockHttpServletResponse();
        var seen = new byte[1][];
        FilterChain chain = (wrappedRequest, wrappedResponse) ->
                seen[0] = ((HttpServletRequest) wrappedRequest).getInputStream().readAllBytes();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(seen[0]).isEqualTo(body);
    }

    private static class UnknownLengthRequest extends MockHttpServletRequest {
        @Override
        public long getContentLengthLong() {
            return -1;
        }
    }
}
