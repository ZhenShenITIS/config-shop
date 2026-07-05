package tg.configshop.external_api.remnawave.dto.user;

import java.util.List;

public record RemnawaveTrafficLimitAndInternalSquadsUpdateRequest(
        String uuid,
        Long trafficLimitBytes,
        List<String> activeInternalSquads
) {
}
