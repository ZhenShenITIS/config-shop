package tg.configshop.external_api.remnawave.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RemnawaveTrafficLimitUpdateRequest(
        String uuid,
        Long id,
        Long trafficLimitBytes
) {
}
