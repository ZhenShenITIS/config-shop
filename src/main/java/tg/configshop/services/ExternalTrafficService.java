package tg.configshop.services;

import tg.configshop.external_api.remnawave.RemnawaveUserRef;
import tg.configshop.dto.RemnawaveLimitedWebhookEvent;

public interface ExternalTrafficService {
    void handleLimitedWebhook(RemnawaveLimitedWebhookEvent event);

    void applyTrafficPurchase(RemnawaveUserRef user, int trafficGb);
}
