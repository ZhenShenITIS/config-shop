package tg.configshop.dto;
import java.util.UUID;
public record NotificationTarget(UUID accountId, UUID subscriptionId) {}
