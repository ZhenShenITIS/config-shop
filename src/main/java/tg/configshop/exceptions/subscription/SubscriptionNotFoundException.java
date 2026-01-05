package tg.configshop.exceptions.subscription;

public class SubscriptionNotFoundException extends RuntimeException {
    public SubscriptionNotFoundException(String message) {
        super(message);
    }

    public SubscriptionNotFoundException() {
    }
}
