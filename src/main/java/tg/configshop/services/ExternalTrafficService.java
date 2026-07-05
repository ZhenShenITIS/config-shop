package tg.configshop.services;

import tg.configshop.dto.RemnawaveLimitedWebhookEvent;

public interface ExternalTrafficService {
    void handleLimitedWebhook(RemnawaveLimitedWebhookEvent event);

    void applyTrafficPurchase(String remnawaveUuid, int trafficGb);
}
