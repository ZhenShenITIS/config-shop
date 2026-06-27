package tg.configshop.listeners;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tg.configshop.events.SubscriptionPaidEvent;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.model.BotUser;
import tg.configshop.model.Subscription;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class RemnawaveEventListener {
    private final RemnawaveClient remnawaveClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSubscriptionPaid(SubscriptionPaidEvent event) {
        updateSubscription(event.botUser(), event.subscription(), event.newExpired());
    }

    private void updateSubscription(BotUser botUser, Subscription subscription, Instant newExpired) {
        remnawaveClient.updateSubscription(botUser.getRemnawaveUuid(), newExpired, subscription.getDeviceCount());
    }

}
