package tg.configshop.quartz;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.quartz.*;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.TestUsers;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.model.*;
import tg.configshop.quartz.jobs.SubscriptionExpiryNotificationJob;
import tg.configshop.quartz.services.SchedulerService;
import tg.configshop.repositories.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationCompatibilityTest {
    private final BotUserRepository users = mock(BotUserRepository.class);
    private final RemnawaveSubscriptionRepository subscriptions = mock(RemnawaveSubscriptionRepository.class);
    private final BotUser user = TestUsers.user(10, "uuid", 123L);

    @Test void schedulingDoesNotDeleteAnotherSubscriptionsJobs() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        when(users.findById(10L)).thenReturn(Optional.of(user));
        var first = user.getSubscription();
        var second = RemnawaveSubscription.builder().id(UUID.randomUUID()).account(user.getAccount()).remnawaveId(124L).build();
        when(subscriptions.findById(first.getId())).thenReturn(Optional.of(first));
        when(subscriptions.findById(second.getId())).thenReturn(Optional.of(second));
        var service = new SchedulerService(scheduler, users, subscriptions);
        Instant expiry = Instant.now().plusSeconds(10 * 86400);
        service.scheduleSubscriptionNotifications(10L, first.getId(), expiry);
        service.scheduleSubscriptionNotifications(10L, second.getId(), expiry);
        var jobs = ArgumentCaptor.forClass(JobDetail.class);
        verify(scheduler, times(4)).scheduleJob(jobs.capture(), any(Trigger.class));
        assertEquals(4, jobs.getAllValues().stream().map(JobDetail::getKey).distinct().count());
        assertTrue(jobs.getAllValues().stream().allMatch(j -> j.getJobDataMap().getLong("userId") == 10L));
        assertEquals(Set.of(first.getId().toString(), second.getId().toString()),
                new HashSet<>(jobs.getAllValues().stream().map(j -> j.getJobDataMap().getString("subscriptionId")).toList()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void legacyJobSupportsNumericAndStringExpiration(boolean numeric) throws Exception {
        when(users.findById(10L)).thenReturn(Optional.of(user));
        Instant expiry = Instant.now().plusSeconds(86400);
        var map = new JobDataMap(); map.put("userId", 10L); map.put("type", "DAYS_LEFT_1");
        map.put("expireAt", numeric ? expiry.toEpochMilli() : expiry.toString());
        var detail = JobBuilder.newJob(SubscriptionExpiryNotificationJob.class).usingJobData(map).build();
        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getJobDetail()).thenReturn(detail);
        var panel = mock(RemnawaveClient.class);
        when(panel.getUser(user.remnawaveRef())).thenReturn(RemnawaveUserResponse.builder().expireAt(expiry).build());
        var telegram = mock(TelegramClient.class);
        new SubscriptionExpiryNotificationJob(users, subscriptions, panel, telegram).execute(context);
        verify(telegram).execute(any(SendMessage.class));
        verifyNoInteractions(subscriptions);
    }

    @Test void schedulingRejectsForeignSubscriptionBeforeWritingJobs() {
        Scheduler scheduler = mock(Scheduler.class);
        var other = TestUsers.user(20, "other", 456L).getSubscription();
        when(users.findById(10L)).thenReturn(Optional.of(user));
        when(subscriptions.findById(other.getId())).thenReturn(Optional.of(other));
        var service = new SchedulerService(scheduler, users, subscriptions);
        assertThrows(IllegalArgumentException.class, () -> service.scheduleSubscriptionNotifications(10L, other.getId(), Instant.now()));
        verifyNoInteractions(scheduler);
    }
}
