package tg.configshop.dto;

import tg.configshop.external_api.remnawave.RemnawaveUserRef;
import java.util.List;

public record RemnawaveLimitedWebhookEvent(
        String event,
        RemnawaveUserRef user,
        Long trafficLimitBytes,
        Long usedTrafficBytes,
        List<String> activeInternalSquadUuids
) {
}
