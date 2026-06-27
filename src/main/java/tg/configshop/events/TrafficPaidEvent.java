package tg.configshop.events;

public record TrafficPaidEvent(
        String remnawaveUuid,
        int trafficGb
) {
}
