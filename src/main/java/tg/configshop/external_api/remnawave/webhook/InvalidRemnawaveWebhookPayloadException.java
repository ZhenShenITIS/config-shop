package tg.configshop.external_api.remnawave.webhook;

public class InvalidRemnawaveWebhookPayloadException extends RuntimeException {
    public InvalidRemnawaveWebhookPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
