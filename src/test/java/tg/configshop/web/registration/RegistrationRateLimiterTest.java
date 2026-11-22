package tg.configshop.web.registration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationRateLimiterTest {
    @Test
    void limitsRegistrationPerAddressAndKeepsOtherClientsAndOperationsIndependent() {
        var limiter = new RegistrationRateLimiter();
        for (int i = 0; i < 5; i++) limiter.register("127.0.0.1");
        var failure = assertThrows(WebAuthException.class, () -> limiter.register("127.0.0.1"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, failure.getStatus());
        assertDoesNotThrow(() -> limiter.register("127.0.0.2"));
        assertDoesNotThrow(() -> limiter.resend("127.0.0.1"));
        assertDoesNotThrow(() -> limiter.confirm("127.0.0.1"));
    }
}
