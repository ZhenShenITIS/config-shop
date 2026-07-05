package tg.configshop.external_api.remnawave.dto.squads;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InternalSquad(
        String uuid,
        String name
) {
}
