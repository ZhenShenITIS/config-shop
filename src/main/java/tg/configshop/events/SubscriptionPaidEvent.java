package tg.configshop.events;
import java.time.Instant;
import java.util.UUID;
public record SubscriptionPaidEvent(UUID accountId, UUID subscriptionId, int devices, Instant newExpired) {}
