package tg.configshop.external_api.remnawave.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tg.configshop.dto.RemnawaveLimitedWebhookEvent;
import tg.configshop.external_api.remnawave.dto.squads.InternalSquad;
import tg.configshop.external_api.remnawave.dto.webhook.RemnawaveWebhookPayload;
import tg.configshop.external_api.remnawave.dto.webhook.RemnawaveWebhookUserData;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class RemnawaveWebhookVerificationService {
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String USER_SCOPE = "user";
    private static final String USER_LIMITED_EVENT = "user.limited";

    private final ObjectMapper objectMapper;
    private final String webhookSecret;

    public RemnawaveWebhookVerificationService(ObjectMapper objectMapper,
                                               @Value("${remnawave.webhook.secret}") String webhookSecret) {
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
    }

    public Optional<RemnawaveLimitedWebhookEvent> verifyAndExtractLimitedEvent(byte[] rawBody, String signatureHeader) {
        if (!isValidSignature(rawBody, signatureHeader)) {
            throw new InvalidRemnawaveWebhookSignatureException();
        }

        RemnawaveWebhookPayload payload = parsePayload(rawBody);
        if (!USER_SCOPE.equals(payload.scope()) || !USER_LIMITED_EVENT.equals(payload.event())) {
            return Optional.empty();
        }

        RemnawaveWebhookUserData data = payload.data();
        if (data == null) {
            return Optional.empty();
        }

        Long usedTrafficBytes = data.userTraffic() == null ? null : data.userTraffic().usedTrafficBytes();
        List<String> activeInternalSquadUuids = data.activeInternalSquads() == null
                ? List.of()
                : data.activeInternalSquads().stream()
                .map(InternalSquad::uuid)
                .toList();

        return Optional.of(new RemnawaveLimitedWebhookEvent(
                payload.event(),
                data.uuid(),
                data.trafficLimitBytes(),
                usedTrafficBytes,
                activeInternalSquadUuids
        ));
    }

    private boolean isValidSignature(byte[] rawBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }

        byte[] expectedSignature = calculateSignature(rawBody).getBytes(StandardCharsets.US_ASCII);
        byte[] actualSignature = signatureHeader.trim().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expectedSignature, actualSignature);
    }

    private String calculateSignature(byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(rawBody));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate Remnawave webhook signature", e);
        }
    }

    private RemnawaveWebhookPayload parsePayload(byte[] rawBody) {
        try {
            return objectMapper.readValue(rawBody, RemnawaveWebhookPayload.class);
        } catch (IOException e) {
            throw new InvalidRemnawaveWebhookPayloadException("Invalid Remnawave webhook payload", e);
        }
    }
}
