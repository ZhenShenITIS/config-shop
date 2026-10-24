package tg.configshop.quartz.jobs;
import lombok.RequiredArgsConstructor;
import org.quartz.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tg.configshop.events.SubscriptionExpiryDueEvent;
import tg.configshop.quartz.constants.ExpirySubNotificationType;
import tg.configshop.quartz.services.NotificationJobSupport;
import tg.configshop.services.ExternalSubscriptionService;
import tg.configshop.util.DateUtil;
import java.time.Instant;
import java.time.Duration;

@Component @RequiredArgsConstructor
public class SubscriptionExpiryNotificationJob implements Job {
    private final NotificationJobSupport support;
    private final ExternalSubscriptionService subscriptions;
    private final ApplicationEventPublisher events;
    @Override public void execute(JobExecutionContext context) throws JobExecutionException {
        var data = context.getJobDetail().getJobDataMap();
        try {
            var target = support.target(data);
            var expiry = subscriptions.getExternalUser(target.accountId(), target.subscriptionId()).expireAt();
            if (!isSameExpireAt(expiry, readExpireAt(data)) || DateUtil.isExpired(expiry)) return;
            events.publishEvent(new SubscriptionExpiryDueEvent(target.accountId(), target.subscriptionId(),
                    ExpirySubNotificationType.valueOf(data.getString("type"))));
        } catch (JobExecutionException e) { throw e; }
        catch (Exception e) { throw new JobExecutionException("Subscription expiry check failed", e); }
    }

    private Instant readExpireAt(JobDataMap dataMap) throws JobExecutionException {
        Object expireAt = dataMap.get("expireAt");
        if (expireAt instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue());
        }
        if (expireAt instanceof String value) {
            try {
                return Instant.parse(value);
            } catch (Exception e) {
                throw new JobExecutionException("Invalid expireAt value: " + value, e);
            }
        }
        throw new JobExecutionException("Missing or invalid expireAt value");
    }

    private boolean isSameExpireAt(Instant currentExpireAt, Instant scheduledExpireAt) {
        if (currentExpireAt == null || scheduledExpireAt == null) {
            return false;
        }
        return Duration.between(currentExpireAt, scheduledExpireAt).abs().compareTo(Duration.ofSeconds(1)) <= 0;
    }

}
