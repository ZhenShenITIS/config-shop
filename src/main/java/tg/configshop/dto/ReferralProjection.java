package tg.configshop.dto;

import java.time.Instant;

public record ReferralProjection(
        Long userId,
        String firstName,
        Long profit,
        Instant referredAt,
        int lvl
) {}
