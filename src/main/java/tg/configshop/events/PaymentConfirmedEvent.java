package tg.configshop.events;
import java.util.UUID;
public record PaymentConfirmedEvent(UUID accountId, long amount, UUID paymentId) {}
