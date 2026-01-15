package tg.configshop.dto;

import lombok.Builder;

import java.time.Instant;

@Builder
public record RemnawaveUser(
        String uuid,
        String username,
        long telegramId,
        String shortUuid,
        double trafficLimitGigaBytes,
        int hwidDeviceLimit,
        UserTrafficInGigabytes userTraffic,
        Instant expireAt,
        String subscriptionUrl,
        String prettyDateExpireAt,
        long daysLeft,
        boolean isActive
) {
}
