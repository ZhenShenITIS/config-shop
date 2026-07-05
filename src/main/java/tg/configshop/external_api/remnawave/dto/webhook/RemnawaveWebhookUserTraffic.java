package tg.configshop.external_api.remnawave.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RemnawaveWebhookUserTraffic(
        Long usedTrafficBytes
) {
}
