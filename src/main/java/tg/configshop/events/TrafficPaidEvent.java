package tg.configshop.events;

import tg.configshop.external_api.remnawave.RemnawaveUserRef;

public record TrafficPaidEvent(
        RemnawaveUserRef user,
        int trafficGb
) {
}
