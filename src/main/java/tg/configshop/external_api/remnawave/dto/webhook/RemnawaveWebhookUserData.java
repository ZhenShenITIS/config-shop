package tg.configshop.external_api.remnawave.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tg.configshop.external_api.remnawave.dto.squads.InternalSquad;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RemnawaveWebhookUserData(
        String uuid,
        Long trafficLimitBytes,
        RemnawaveWebhookUserTraffic userTraffic,
        List<InternalSquad> activeInternalSquads
) {
}
