package tg.configshop.external_api.remnawave.dto.device;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeleteDeviceRequest(
        String userUuid,
        Long userId,
        String hwid
) {
}
