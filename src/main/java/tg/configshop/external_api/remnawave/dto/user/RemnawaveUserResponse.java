package tg.configshop.external_api.remnawave.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import tg.configshop.external_api.remnawave.dto.squads.InternalSquad;

import java.time.Instant;
import java.util.List;

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
        String subscriptionUrl,
        List<InternalSquad> activeInternalSquads
) {
}
