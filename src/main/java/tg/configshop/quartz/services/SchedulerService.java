package tg.configshop.quartz.services;

import tg.configshop.services.SubscriptionAccessService;
import tg.configshop.services.LegacyNotificationResolver;
import org.springframework.beans.factory.ObjectProvider;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.stereotype.Service;
import tg.configshop.quartz.constants.ExpirySubNotificationType;
import tg.configshop.quartz.constants.TrialNotificationType;
import tg.configshop.quartz.jobs.NoTrialTrafficNotification;
import tg.configshop.quartz.jobs.SubscriptionExpiryNotificationJob;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerService {

    private final Scheduler scheduler;
    private final SubscriptionAccessService subscriptions;
    private final ObjectProvider<LegacyNotificationResolver> legacy;

    private static final String SUBSCRIPTION_GROUP = "subscription_notifications";
    private static final String TRIAL_GROUP = "trial_checks";

    public void scheduleSubscriptionNotifications(UUID accountId, UUID subscriptionId, Instant expireAt) {
        subscriptions.requireOwned(accountId, subscriptionId);
        try {

            scheduleSubscriptionJob(accountId, subscriptionId, expireAt, Duration.ofDays(3), ExpirySubNotificationType.DAYS_LEFT_3.name());

            scheduleSubscriptionJob(accountId, subscriptionId, expireAt, Duration.ofDays(1), ExpirySubNotificationType.DAYS_LEFT_1.name());

            log.info("Scheduled subscription notifications for account {}", accountId);

        } catch (SchedulerException e) {
            log.error("Failed to schedule subscription notifications: {}", e.getMessage());
        }
    }

    private void scheduleSubscriptionJob(UUID accountId, UUID subscriptionUuid, Instant expireAt,
                                         Duration beforeExpiry, String type)
            throws SchedulerException {

        Instant executeAt = expireAt.minus(beforeExpiry);
        String subscriptionId = subscriptionUuid.toString();
        String jobName = String.format("sub_notify_%s_%s", type, subscriptionId);
        var resolver = legacy.getIfAvailable();
        for (Long recipientId : resolver == null ? java.util.List.<Long>of() : resolver.recipientIds(accountId)) {
            JobKey legacyKey = JobKey.jobKey(String.format("sub_notify_%s_%d", type, recipientId), SUBSCRIPTION_GROUP);
            if (scheduler.checkExists(legacyKey)) {
                JobDetail old = scheduler.getJobDetail(legacyKey);
                if (old == null) continue;
                String oldSubscriptionId = old.getJobDataMap().getString("subscriptionId");
                if (oldSubscriptionId == null) {
                    try { oldSubscriptionId = resolver.subscriptionId(recipientId).toString(); }
                    catch (IllegalStateException unresolved) { continue; }
                }
                if (subscriptionId.equals(oldSubscriptionId)) scheduler.deleteJob(legacyKey);
            }
        }
        JobKey jobKey = JobKey.jobKey(jobName, SUBSCRIPTION_GROUP);

        if (!executeAt.isAfter(Instant.now())) {
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
                log.debug("Deleted outdated job: {}", jobName);
            }
            log.debug("Skipping job {} because execution time {} is not in the future", jobName, executeAt);
            return;
        }

        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
            log.debug("Deleted existing job: {}", jobName);
        }

        JobDetail job = JobBuilder.newJob(SubscriptionExpiryNotificationJob.class)
                .withIdentity(jobKey)
                .usingJobData("accountId", accountId.toString())
                .usingJobData("subscriptionId", subscriptionId)
                .usingJobData("type", type)
                .usingJobData("expireAt", expireAt.toString())
                .storeDurably(false)
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobName + "_trigger", SUBSCRIPTION_GROUP)
                .startAt(Date.from(executeAt))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule())
                .build();

        scheduler.scheduleJob(job, trigger);

        log.debug("Scheduled job {} at {}", jobName, executeAt);
    }


    public void scheduleTrialTrafficChecks(UUID accountId, UUID subscriptionId) {
        subscriptions.requireOwned(accountId, subscriptionId);
        try {
            Instant now = Instant.now();

            scheduleTrafficCheckJob(accountId, subscriptionId, now.plus(Duration.ofHours(12)), TrialNotificationType.HOUR_12.name());


            scheduleTrafficCheckJob(accountId, subscriptionId, now.plus(Duration.ofDays(1)), TrialNotificationType.HOUR_24.name());


            scheduleTrafficCheckJob(accountId, subscriptionId, now.plus(Duration.ofDays(2)), TrialNotificationType.HOUR_48.name());



            log.info("Scheduled trial traffic checks for account {}", accountId);

        } catch (SchedulerException e) {
            log.error("Failed to schedule trial checks: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void scheduleTrafficCheckJob(UUID accountId, UUID subscriptionUuid, Instant executeAt, String type)
            throws SchedulerException {

        String subscriptionId = subscriptionUuid.toString();
        String jobName = String.format("trial_check_%s_%s", type, subscriptionId);
        JobKey jobKey = JobKey.jobKey(jobName, TRIAL_GROUP);

        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
            log.debug("Deleted existing job: {}", jobName);
        }

        JobDetail job = JobBuilder.newJob(NoTrialTrafficNotification.class)
                .withIdentity(jobKey)
                .usingJobData("accountId", accountId.toString())
                .usingJobData("subscriptionId", subscriptionId)
                .usingJobData("type", type)
                .storeDurably(false)
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobName + "_trigger", TRIAL_GROUP)
                .startAt(Date.from(executeAt))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule())
                .build();

        scheduler.scheduleJob(job, trigger);

        log.debug("Scheduled traffic check job {} at {}", jobName, executeAt);
    }


}
