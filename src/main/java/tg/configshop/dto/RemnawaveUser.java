package tg.configshop.dto;

import lombok.Builder;

import java.time.Instant;

@Builder
public record RemnawaveUser(
        String uuid,
        Long id,
        String username,
        String shortUuid,
        double trafficLimitGigaBytes,
        int hwidDeviceLimit,
        UserTrafficInGigabytes userTraffic,
        Instant expireAt,
        String subscriptionUrl,
        long daysLeft,
        boolean isActive
) {
}
