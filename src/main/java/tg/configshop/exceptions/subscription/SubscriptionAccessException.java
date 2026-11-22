package tg.configshop.exceptions.subscription;

public class SubscriptionAccessException extends IllegalArgumentException {
    public SubscriptionAccessException() {
        super("Subscription not found or belongs to another account");
    }
}
