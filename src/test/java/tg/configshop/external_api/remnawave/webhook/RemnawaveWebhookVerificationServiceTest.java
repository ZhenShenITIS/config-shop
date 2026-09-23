package tg.configshop.external_api.remnawave.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tg.configshop.dto.RemnawaveLimitedWebhookEvent;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemnawaveWebhookVerificationServiceTest {
    private static final String SECRET = "test-secret";

    private final RemnawaveWebhookVerificationService service = new RemnawaveWebhookVerificationService(
            new ObjectMapper().findAndRegisterModules(),
            SECRET
    );

    @Test
    void verifyAndExtractLimitedEventReturnsCleanDtoForValidSignature() {
        byte[] rawBody = limitedPayload().getBytes(StandardCharsets.UTF_8);
        String signature = sign(rawBody);

        Optional<RemnawaveLimitedWebhookEvent> event = service.verifyAndExtractLimitedEvent(rawBody, signature);

        assertTrue(event.isPresent());
        assertEquals("user.limited", event.get().event());
        assertEquals("9d96d9d2-9b88-4f8f-8c1c-3e8f7d84206d", event.get().user().uuid());
        assertEquals(1073741824L, event.get().trafficLimitBytes());
        assertEquals(1073741824L, event.get().usedTrafficBytes());
        assertEquals(2, event.get().activeInternalSquadUuids().size());
    }

    @Test
    void verifyAndExtractLimitedEventRejectsInvalidSignature() {
        byte[] rawBody = limitedPayload().getBytes(StandardCharsets.UTF_8);

        assertThrows(
                InvalidRemnawaveWebhookSignatureException.class,
                () -> service.verifyAndExtractLimitedEvent(rawBody, "invalid-signature")
        );
    }

    @Test
    void verifyAndExtractLimitedEventIgnoresOtherEvents() {
        byte[] rawBody = limitedPayload().replace("user.limited", "user.modified").getBytes(StandardCharsets.UTF_8);
        String signature = sign(rawBody);

        Optional<RemnawaveLimitedWebhookEvent> event = service.verifyAndExtractLimitedEvent(rawBody, signature);

        assertFalse(event.isPresent());
    }

    @Test
    void acceptsV2PayloadWithBothIdentifiers() {
        byte[] body = limitedPayload().replace("\"data\":{", "\"data\":{\"id\":9876543210,")
                .getBytes(StandardCharsets.UTF_8);
        RemnawaveLimitedWebhookEvent event = service.verifyAndExtractLimitedEvent(body, sign(body)).orElseThrow();
        assertEquals(9876543210L, event.user().id());
        assertEquals("9d96d9d2-9b88-4f8f-8c1c-3e8f7d84206d", event.user().uuid());
    }

    @Test
    void acceptsV3PayloadWithoutUuid() {
        byte[] body = limitedPayload().replace(
                "\"uuid\":\"9d96d9d2-9b88-4f8f-8c1c-3e8f7d84206d\"", "\"id\":9876543210")
                .getBytes(StandardCharsets.UTF_8);
        RemnawaveLimitedWebhookEvent event = service.verifyAndExtractLimitedEvent(body, sign(body)).orElseThrow();
        assertEquals(9876543210L, event.user().id());
        assertEquals(null, event.user().uuid());
    }

    @Test
    void rejectsLimitedEventWithoutIdentifier() {
        byte[] body = limitedPayload().replace(
                "\"uuid\":\"9d96d9d2-9b88-4f8f-8c1c-3e8f7d84206d\",", "")
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(InvalidRemnawaveWebhookPayloadException.class,
                () -> service.verifyAndExtractLimitedEvent(body, sign(body)));
    }

    @Test
    void rejectsNonPositiveId() {
        byte[] body = limitedPayload().replace("\"data\":{", "\"data\":{\"id\":0,")
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(InvalidRemnawaveWebhookPayloadException.class,
                () -> service.verifyAndExtractLimitedEvent(body, sign(body)));
    }

    private static String limitedPayload() {
        return """
                {"scope":"user","event":"user.limited","timestamp":"2026-07-05T11:32:00.000Z","data":{"uuid":"9d96d9d2-9b88-4f8f-8c1c-3e8f7d84206d","trafficLimitBytes":1073741824,"userTraffic":{"usedTrafficBytes":1073741824},"activeInternalSquads":[{"uuid":"11111111-1111-1111-1111-111111111111","name":"whitelist","extra":"ignored"},{"uuid":"22222222-2222-2222-2222-222222222222","name":"traf-limit-serv"}]},"meta":null}
                """;
    }

    private static String sign(byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawBody));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
