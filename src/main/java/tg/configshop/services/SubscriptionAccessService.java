package tg.configshop.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.dto.SubscriptionSummary;
import tg.configshop.exceptions.subscription.SubscriptionAccessException;
import tg.configshop.model.RemnawaveSubscription;
import tg.configshop.repositories.RemnawaveSubscriptionRepository;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionAccessService {
    private final RemnawaveSubscriptionRepository subscriptions;
    private final AccountService accounts;

    @Transactional(readOnly = true)
    public List<SubscriptionSummary> list(UUID accountId) {
        accounts.requireAccount(accountId);
        return subscriptions.findByAccount_IdOrderByCreatedAtAscIdAsc(accountId).stream()
                .map(s -> new SubscriptionSummary(s.getId(), accountId, s.getShortId())).toList();
    }

    public RemnawaveSubscription requireOwned(UUID accountId, UUID subscriptionId) {
        return subscriptions.findByIdAndAccount_Id(subscriptionId, accountId)
                .orElseThrow(SubscriptionAccessException::new);
    }

    public RemnawaveSubscription requireExisting(UUID subscriptionId) {
        return subscriptions.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
    }
}
