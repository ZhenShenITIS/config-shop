package tg.configshop.events;

import tg.configshop.model.BotUser;
import tg.configshop.model.Subscription;

import java.time.Instant;

public record SubscriptionPaidEvent(
        BotUser botUser,
        Subscription subscription,
        Instant newExpired
) {
}
