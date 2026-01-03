package tg.configshop.external_api.remnawave.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.time.Instant;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemnawaveUserResponse(
        String uuid,
        String username,
        Long telegramId,
        String shortUuid,
        Long trafficLimitBytes,
        Integer hwidDeviceLimit,
        UserTraffic userTraffic,
        Instant expireAt,
        String subscriptionUrl
) {
}
