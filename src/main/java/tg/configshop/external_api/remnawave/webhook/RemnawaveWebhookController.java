package tg.configshop.external_api.remnawave.webhook;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.configshop.dto.RemnawaveLimitedWebhookEvent;
import tg.configshop.services.ExternalTrafficService;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/remnawave/webhook")
public class RemnawaveWebhookController {
    private static final String SIGNATURE_HEADER = "X-Remnawave-Signature";

    private final RemnawaveWebhookVerificationService verificationService;
    private final ExternalTrafficService externalTrafficService;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(@RequestBody byte[] rawBody,
                                              @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature) {
        try {
            Optional<RemnawaveLimitedWebhookEvent> event = verificationService.verifyAndExtractLimitedEvent(rawBody, signature);
            event.ifPresent(externalTrafficService::handleLimitedWebhook);
            return ResponseEntity.ok().build();
        } catch (InvalidRemnawaveWebhookSignatureException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (InvalidRemnawaveWebhookPayloadException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
