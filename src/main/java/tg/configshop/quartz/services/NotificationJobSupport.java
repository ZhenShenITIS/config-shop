package tg.configshop.quartz.services;
import lombok.RequiredArgsConstructor;
import org.quartz.JobDataMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tg.configshop.dto.NotificationTarget;
import tg.configshop.services.LegacyNotificationResolver;
import tg.configshop.services.SubscriptionAccessService;
import java.util.UUID;

@Component @RequiredArgsConstructor
public class NotificationJobSupport {
    private final SubscriptionAccessService subscriptions;
    private final ObjectProvider<LegacyNotificationResolver> legacy;
    public NotificationTarget target(JobDataMap data) {
        UUID accountId;
        UUID subscriptionId;
        if (data.containsKey("accountId")) {
            accountId = UUID.fromString(data.getString("accountId"));
            subscriptionId = UUID.fromString(data.getString("subscriptionId"));
        } else {
            var resolver = legacy.getIfAvailable();
            if (resolver == null) throw new IllegalStateException("Legacy notification resolver is unavailable");
            long recipient = data.getLongValue("userId");
            accountId = resolver.accountId(recipient);
            subscriptionId = data.containsKey("subscriptionId") ? UUID.fromString(data.getString("subscriptionId"))
                    : resolver.subscriptionId(recipient);
        }
        subscriptions.requireOwned(accountId, subscriptionId);
        return new NotificationTarget(accountId, subscriptionId);
    }
}
