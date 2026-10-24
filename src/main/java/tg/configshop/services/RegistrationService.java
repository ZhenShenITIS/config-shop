package tg.configshop.services;
import java.util.UUID;
import tg.configshop.dto.InitialSubscriptionData;
import tg.configshop.dto.SubscriptionSummary;
public interface RegistrationService {
    SubscriptionSummary createTrialSubscription(UUID accountId);
    SubscriptionSummary registerInitialSubscription(UUID accountId, InitialSubscriptionData initial);
}
