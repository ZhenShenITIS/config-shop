package tg.configshop.dto;

import java.time.Instant;

public record ReferralProjection(
        java.util.UUID accountId,
        Long profit,
        Instant referredAt,
        int lvl
) {}
