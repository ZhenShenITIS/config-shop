package tg.configshop.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tg.configshop.dto.PlategaCallback;
import tg.configshop.services.PlategaCallbackService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.UUID;

@RestController
public class PlategaWebhookController {
    private static final Set<String> PAYMENT_STATUSES = Set.of(
            "CONFIRMED", "CANCELED", "CHARGEBACKED", "PARTIALLY_REFUNDED", "DISPUTED");
    private final PlategaCallbackService callbacks;
    private final byte[] merchantId;
    private final byte[] secret;

    public PlategaWebhookController(PlategaCallbackService callbacks, @Value("${PAY_MERCHANT_ID}") String merchantId,
                                    @Value("${PAY_SECRET}") String secret) {
        if (merchantId.isBlank() || secret.isBlank()) throw new IllegalArgumentException("Configure Platega credentials");
        this.callbacks = callbacks;
        this.merchantId = merchantId.getBytes(StandardCharsets.UTF_8);
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping(path = "/api/webhooks/platega", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void callback(@RequestHeader(name = "X-MerchantId", required = false) String receivedMerchant,
                         @RequestHeader(name = "X-Secret", required = false) String receivedSecret,
                         @RequestBody CallbackBody body) {
        boolean authorizedMerchant = matches(merchantId, receivedMerchant);
        boolean authorizedSecret = matches(secret, receivedSecret);
        if (!authorizedMerchant || !authorizedSecret) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (body.id() == null || body.status() == null || body.status().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (!PAYMENT_STATUSES.contains(body.status())) return;
        if (body.amount() == null || body.amount().signum() <= 0 || body.currency() == null || body.paymentMethod() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        try {
            callbacks.applyCallback(new PlategaCallback(body.id().toString(), body.amount(), body.currency(),
                    body.status(), body.paymentMethod(), body.payload()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Payment details do not match");
        }
    }

    private static boolean matches(byte[] expected, String received) {
        return received != null && MessageDigest.isEqual(expected, received.getBytes(StandardCharsets.UTF_8));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackBody(UUID id, BigDecimal amount, String currency, String status, Integer paymentMethod,
                               String payload) {}
}
