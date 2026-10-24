package tg.configshop.dto;
import java.util.UUID;
public record SubscriptionSummary(UUID id, UUID accountId, String shortId) {}
