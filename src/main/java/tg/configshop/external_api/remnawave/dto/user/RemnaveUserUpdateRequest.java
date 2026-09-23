package tg.configshop.external_api.remnawave.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RemnaveUserUpdateRequest (
        String uuid,
        Long id,
        Instant expireAt,
        Integer hwidDeviceLimit
){
}
