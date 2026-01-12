package tg.configshop.dto;

import tg.configshop.model.BotUser;

import java.time.Instant;

public record ReferralWithProfitAndLevel(
        BotUser botUser,
        Long profit,
        Instant referredAt,
        int lvl
) {
}
