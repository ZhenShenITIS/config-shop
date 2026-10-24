package tg.configshop.dto;
import java.time.Instant;
import java.util.UUID;
public record ReferralWithProfitAndLevel(UUID accountId, Long profit, Instant referredAt, int lvl) {}
