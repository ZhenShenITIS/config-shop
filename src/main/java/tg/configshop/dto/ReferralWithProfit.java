package tg.configshop.dto;

import tg.configshop.model.BotUser;

public record ReferralWithProfit (
        BotUser botUser,
        Long profit
) {
}
