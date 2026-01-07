package tg.configshop.dto;

import tg.configshop.model.BotUser;

import java.time.Instant;

public record ReferralWithProfit (
        BotUser botUser,
        Long profit,
        Instant referredAt
) {
}
