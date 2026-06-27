package tg.configshop.quartz.services;

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

    private static final String SUBSCRIPTION_GROUP = "subscription_notifications";
    private static final String TRIAL_GROUP = "trial_checks";

    public void scheduleSubscriptionNotifications(Long userId, Instant expireAt) {
        try {

            scheduleSubscriptionJob(userId, expireAt, Duration.ofDays(3), ExpirySubNotificationType.DAYS_LEFT_3.name());

            scheduleSubscriptionJob(userId, expireAt, Duration.ofDays(1), ExpirySubNotificationType.DAYS_LEFT_1.name());

            log.info("Scheduled subscription notifications for user {}", userId);

        } catch (SchedulerException e) {
            log.error("Failed to schedule subscription notifications: {}", e.getMessage());
        }
    }

    private void scheduleSubscriptionJob(Long userId, Instant expireAt,
                                         Duration beforeExpiry, String type)
            throws SchedulerException {

        Instant executeAt = expireAt.minus(beforeExpiry);
        String jobName = String.format("sub_notify_%s_%d", type, userId);
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
        try {
            Instant now = Instant.now();

            scheduleTrafficCheckJob(userId, now.plus(Duration.ofHours(12)), TrialNotificationType.HOUR_12.name());


            scheduleTrafficCheckJob(userId, now.plus(Duration.ofDays(1)), TrialNotificationType.HOUR_24.name());


            scheduleTrafficCheckJob(userId, now.plus(Duration.ofDays(2)), TrialNotificationType.HOUR_48.name());



            log.info("Scheduled trial traffic checks for user {}", userId);

        } catch (SchedulerException e) {
            log.error("Failed to schedule trial checks: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void scheduleTrafficCheckJob(Long userId, Instant executeAt, String type)
            throws SchedulerException {

        String jobName = String.format("trial_check_%s_%d", type, userId);
        JobKey jobKey = JobKey.jobKey(jobName, TRIAL_GROUP);

        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
            log.debug("Deleted existing job: {}", jobName);
        }

        JobDetail job = JobBuilder.newJob(NoTrialTrafficNotification.class)
                .withIdentity(jobKey)
                .usingJobData("userId", userId)
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
