package tg.configshop.events;
import java.util.UUID;
public record ReferralCreatedEvent(UUID referrerAccountId, UUID referralAccountId) {}
