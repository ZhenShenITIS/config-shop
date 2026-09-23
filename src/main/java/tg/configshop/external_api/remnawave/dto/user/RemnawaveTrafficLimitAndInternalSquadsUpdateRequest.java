package tg.configshop.external_api.remnawave.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RemnawaveTrafficLimitAndInternalSquadsUpdateRequest(
        String uuid,
        Long id,
        Long trafficLimitBytes,
        List<String> activeInternalSquads
) {
}
