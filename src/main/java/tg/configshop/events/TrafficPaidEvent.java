package tg.configshop.events;
import java.util.UUID;
public record TrafficPaidEvent(UUID accountId, UUID subscriptionId, int trafficGb) {}
