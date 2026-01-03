package tg.configshop.external_api.remnawave.dto.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceRootResponse(
        DeviceResponse response
) {
}
