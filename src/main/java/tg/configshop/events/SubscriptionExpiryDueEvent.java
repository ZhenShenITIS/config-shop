package tg.configshop.events;
import java.util.UUID;
import tg.configshop.quartz.constants.ExpirySubNotificationType;
public record SubscriptionExpiryDueEvent(UUID accountId, UUID subscriptionId, ExpirySubNotificationType type) {}
