package tg.configshop.external_api.remnawave.dto.user;

import java.time.Instant;

public record RemnaveUserUpdateRequest (
        String uuid,
        Instant expireAt,
        Long trafficLimitBytes,
        Integer hwidDeviceLimit
){
}
