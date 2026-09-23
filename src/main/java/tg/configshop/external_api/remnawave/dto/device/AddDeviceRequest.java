package tg.configshop.external_api.remnawave.dto.device;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddDeviceRequest (
        int hwidDeviceLimit,
        String uuid,
        Long id
) {

}
