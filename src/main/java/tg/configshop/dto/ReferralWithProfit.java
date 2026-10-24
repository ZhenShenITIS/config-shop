package tg.configshop.dto;

import java.util.UUID;

import java.time.Instant;

public record ReferralWithProfit (
        UUID accountId,
        Long profit,
        Instant referredAt
) {
}
