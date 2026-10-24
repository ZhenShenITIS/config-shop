package tg.configshop.external_api.pay;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tg.configshop.external_api.pay.config.PlategaConfig;
import tg.configshop.external_api.pay.constants.PaymentStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PlategaClientTest {
    @Test void readsStatusAndExactPaymentDetails() {
        RestClient.Builder builder = new PlategaConfig().plategaRestClient("https://pay.test", "merchant", "secret").mutate();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new PlategaClientImpl(builder.build());
        server.expect(requestTo("https://pay.test/transaction/payment-id"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-MerchantId", "merchant"))
                .andExpect(header("X-Secret", "secret"))
                .andRespond(withSuccess("""
                        {"id":"payment-id","status":"CONFIRMED",
                         "paymentDetails":{"amount":1000.50,"currency":"RUB"},"extra":"ignored"}
                        """, MediaType.APPLICATION_JSON));
        var payment = client.getPayment("payment-id");
        assertEquals("payment-id", payment.id());
        assertEquals(PaymentStatus.CONFIRMED, payment.status());
        assertEquals(0, new BigDecimal("1000.50").compareTo(payment.paymentDetails().amount()));
        assertEquals("RUB", payment.paymentDetails().currency());
        server.verify();
    }

    @Test void preservesRateLimitResponseAndRetryAfter() {
        var builder = RestClient.builder().baseUrl("https://pay.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new PlategaClientImpl(builder.build());
        server.expect(requestTo("https://pay.test/transaction/payment-id"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "120"));
        var error = assertThrows(HttpClientErrorException.TooManyRequests.class, () -> client.getPayment("payment-id"));
        assertEquals("120", error.getResponseHeaders().getFirst("Retry-After"));
        server.verify();
    }
}
