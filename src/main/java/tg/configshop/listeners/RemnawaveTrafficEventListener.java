package tg.configshop.listeners;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tg.configshop.events.TrafficPaidEvent;
import tg.configshop.services.ExternalTrafficService;

@Component
@RequiredArgsConstructor
public class RemnawaveTrafficEventListener {
    private final ExternalTrafficService externalTrafficService;
    private final tg.configshop.services.SubscriptionAccessService subscriptions;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTrafficPaid(TrafficPaidEvent event) {
        externalTrafficService.applyTrafficPurchase(subscriptions.requireOwned(event.accountId(), event.subscriptionId()).remnawaveRef(), event.trafficGb());
    }
}
