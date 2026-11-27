package tg.configshop.exceptions.subscription;

public class SubscriptionChangeInProgressException extends IllegalStateException {
    public SubscriptionChangeInProgressException() {
        super("A subscription change is already in progress");
    }
}
