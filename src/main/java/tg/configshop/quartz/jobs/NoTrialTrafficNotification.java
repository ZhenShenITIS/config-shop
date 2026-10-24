package tg.configshop.quartz.jobs;
import lombok.RequiredArgsConstructor;
import org.quartz.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tg.configshop.events.TrialTrafficUnusedEvent;
import tg.configshop.quartz.constants.TrialNotificationType;
import tg.configshop.quartz.services.NotificationJobSupport;
import tg.configshop.services.ExternalSubscriptionService;

@Component @RequiredArgsConstructor
public class NoTrialTrafficNotification implements Job {
    private final NotificationJobSupport support;
    private final ExternalSubscriptionService subscriptions;
    private final ApplicationEventPublisher events;
    @Override public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            var data = context.getJobDetail().getJobDataMap();
            var target = support.target(data);
            var type = TrialNotificationType.valueOf(data.getString("type"));
            var subscription = subscriptions.getExternalUser(target.accountId(), target.subscriptionId());
            if (subscription.isActive() && subscription.userTraffic().usedTraffic() == 0)
                events.publishEvent(new TrialTrafficUnusedEvent(target.accountId(), target.subscriptionId(), type));
        } catch (Exception e) { throw new JobExecutionException("Trial traffic check failed", e); }
    }
}
