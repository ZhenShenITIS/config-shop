package tg.configshop.external_api.remnawave.dto.user;

import java.time.Instant;
import java.util.List;

public record RemnawaveUserRequest(
        String username,
        Instant expireAt,
        Long telegramId,
        Long trafficLimitBytes,
        Integer hwidDeviceLimit,
        List<String> activeInternalSquads
) {
}
