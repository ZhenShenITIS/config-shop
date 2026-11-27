package tg.configshop.exceptions.subscription;

public class SubscriptionChangeConflictException extends IllegalArgumentException {
    public SubscriptionChangeConflictException() {
        super("Subscription purchase parameters changed");
    }
}
