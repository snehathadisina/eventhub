package com.eventhub.eventhub_backend.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(new RateLimiter());
        ReflectionTestUtils.setField(filter, "authPerMinute", 3);
    }

    @Test
    void theFourthAttemptFromOneAddressIsBlocked() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(call("POST", "/api/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = call("POST", "/api/auth/login", "10.0.0.1");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
        assertThat(blocked.getContentAsString()).contains("Too many attempts");
    }

    @Test
    void anotherAddressHasItsOwnAllowance() throws Exception {
        for (int i = 0; i < 4; i++) {
            call("POST", "/api/auth/login", "10.0.0.1");
        }

        assertThat(call("POST", "/api/auth/login", "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void loginRegisterAndGoogleShareOneAllowance() throws Exception {
        call("POST", "/api/auth/login", "10.0.0.3");
        call("POST", "/api/auth/register", "10.0.0.3");
        call("POST", "/api/auth/google", "10.0.0.3");

        assertThat(call("POST", "/api/auth/login", "10.0.0.3").getStatus()).isEqualTo(429);
    }

    @Test
    void otherRequestsAreNeverLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("GET", "/api/auth/login", "10.0.0.4").getStatus()).isEqualTo(200);
            assertThat(call("POST", "/api/bookings", "10.0.0.4").getStatus()).isEqualTo(200);
            assertThat(call("GET", "/api/events", "10.0.0.4").getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse call(String method, String path, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}