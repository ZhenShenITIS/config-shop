package tg.configshop.quartz.services;

import tg.configshop.repositories.RemnawaveSubscriptionRepository;
import tg.configshop.repositories.BotUserRepository;

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
    private final BotUserRepository users;
    private final RemnawaveSubscriptionRepository subscriptions;

    private static final String SUBSCRIPTION_GROUP = "subscription_notifications";
    private static final String TRIAL_GROUP = "trial_checks";

    public void scheduleSubscriptionNotifications(Long userId, Instant expireAt) {
        scheduleSubscriptionNotifications(userId, users.findById(userId).orElseThrow().getSubscription().getId(), expireAt);
    }

    public void scheduleSubscriptionNotifications(Long userId, java.util.UUID subscriptionId, Instant expireAt) {
        checkOwner(userId, subscriptionId);
        try {

            scheduleSubscriptionJob(userId, subscriptionId, expireAt, Duration.ofDays(3), ExpirySubNotificationType.DAYS_LEFT_3.name());

            scheduleSubscriptionJob(userId, subscriptionId, expireAt, Duration.ofDays(1), ExpirySubNotificationType.DAYS_LEFT_1.name());

            log.info("Scheduled subscription notifications for user {}", userId);

        } catch (SchedulerException e) {
            log.error("Failed to schedule subscription notifications: {}", e.getMessage());
        }
    }

    private void scheduleSubscriptionJob(Long userId, java.util.UUID subscriptionUuid, Instant expireAt,
                                         Duration beforeExpiry, String type)
            throws SchedulerException {

        Instant executeAt = expireAt.minus(beforeExpiry);
        String subscriptionId = subscriptionUuid.toString();
        String jobName = String.format("sub_notify_%s_%s", type, subscriptionId);
        JobKey legacyKey = JobKey.jobKey(String.format("sub_notify_%s_%d", type, userId), SUBSCRIPTION_GROUP);
        if (scheduler.checkExists(legacyKey)) scheduler.deleteJob(legacyKey);
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
                .usingJobData("userId", userId)
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


    public void scheduleTrialTrafficChecks(Long userId) {
        scheduleTrialTrafficChecks(userId, users.findById(userId).orElseThrow().getSubscription().getId());
    }

    public void scheduleTrialTrafficChecks(Long userId, java.util.UUID subscriptionId) {
        checkOwner(userId, subscriptionId);
        try {
            Instant now = Instant.now();

            scheduleTrafficCheckJob(userId, subscriptionId, now.plus(Duration.ofHours(12)), TrialNotificationType.HOUR_12.name());


            scheduleTrafficCheckJob(userId, subscriptionId, now.plus(Duration.ofDays(1)), TrialNotificationType.HOUR_24.name());


            scheduleTrafficCheckJob(userId, subscriptionId, now.plus(Duration.ofDays(2)), TrialNotificationType.HOUR_48.name());



            log.info("Scheduled trial traffic checks for user {}", userId);

        } catch (SchedulerException e) {
            log.error("Failed to schedule trial checks: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void scheduleTrafficCheckJob(Long userId, java.util.UUID subscriptionUuid, Instant executeAt, String type)
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
                .usingJobData("userId", userId)
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


    private void checkOwner(Long telegramId, java.util.UUID subscriptionId) {
        var user = users.findById(telegramId).orElseThrow();
        var subscription = subscriptions.findById(subscriptionId).orElseThrow();
        if (!subscription.getAccount().getId().equals(user.getAccount().getId()))
            throw new IllegalArgumentException("Subscription belongs to another account");
    }

}
