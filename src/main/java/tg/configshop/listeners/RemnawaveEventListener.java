package tg.configshop.listeners;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tg.configshop.events.SubscriptionPaidEvent;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.services.SubscriptionAccessService;

@Component @RequiredArgsConstructor
public class RemnawaveEventListener {
    private final RemnawaveClient remnawaveClient;
    private final SubscriptionAccessService subscriptions;
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSubscriptionPaid(SubscriptionPaidEvent event) {
        var subscription = subscriptions.requireOwned(event.accountId(), event.subscriptionId());
        remnawaveClient.updateSubscription(subscription.remnawaveRef(), event.newExpired(), event.devices());
    }
}
