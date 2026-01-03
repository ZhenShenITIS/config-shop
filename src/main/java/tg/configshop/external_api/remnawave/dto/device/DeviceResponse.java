package tg.configshop.external_api.remnawave.dto.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceResponse(
        List<Device> devices,
        int total
) {
}
