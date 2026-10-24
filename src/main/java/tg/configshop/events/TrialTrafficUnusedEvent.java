package tg.configshop.events;
import java.util.UUID;
import tg.configshop.quartz.constants.TrialNotificationType;
public record TrialTrafficUnusedEvent(UUID accountId, UUID subscriptionId, TrialNotificationType type) {}
