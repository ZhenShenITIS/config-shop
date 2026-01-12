package tg.configshop.dto;

import java.time.Instant;

public record ReferralProjection(
        Long userId,
        String firstName,
        Instant expireAt,
        Long profit,
        Instant referredAt,
        int lvl
) {}