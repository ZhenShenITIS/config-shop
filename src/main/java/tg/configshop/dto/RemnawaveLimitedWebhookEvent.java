package tg.configshop.dto;

import java.util.List;

public record RemnawaveLimitedWebhookEvent(
        String event,
        String remnawaveUuid,
        Long trafficLimitBytes,
        Long usedTrafficBytes,
        List<String> activeInternalSquadUuids
) {
}
