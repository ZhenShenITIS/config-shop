package tg.configshop.quartz;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.quartz.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import tg.configshop.dto.RemnawaveUser;
import tg.configshop.events.SubscriptionExpiryDueEvent;
import tg.configshop.model.*;
import tg.configshop.quartz.jobs.SubscriptionExpiryNotificationJob;
import tg.configshop.quartz.services.*;
import tg.configshop.services.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationCompatibilityTest {
    private final SubscriptionAccessService subscriptions=mock(SubscriptionAccessService.class);
    private final LegacyNotificationResolver resolver=mock(LegacyNotificationResolver.class);
    @SuppressWarnings("unchecked") private final ObjectProvider<LegacyNotificationResolver> legacy=mock(ObjectProvider.class);
    private final UUID accountId=UUID.randomUUID(), firstId=UUID.randomUUID(), secondId=UUID.randomUUID();
    private void prepareLegacy() {
        when(legacy.getIfAvailable()).thenReturn(resolver);
        when(resolver.accountId(10L)).thenReturn(accountId);
        when(resolver.subscriptionId(10L)).thenReturn(firstId);
        when(resolver.recipientIds(accountId)).thenReturn(List.of(10L));
    }
    @Test void schedulingDoesNotDeleteAnotherSubscriptionsJobs() throws Exception {
        prepareLegacy(); var scheduler=mock(Scheduler.class);
        var service=new SchedulerService(scheduler,subscriptions,legacy);
        Instant expiry=Instant.now().plusSeconds(10*86400);
        service.scheduleSubscriptionNotifications(accountId,firstId,expiry);
        service.scheduleSubscriptionNotifications(accountId,secondId,expiry);
        var jobs=ArgumentCaptor.forClass(JobDetail.class);
        verify(scheduler,times(4)).scheduleJob(jobs.capture(),any(Trigger.class));
        assertEquals(4,jobs.getAllValues().stream().map(JobDetail::getKey).distinct().count());
        assertTrue(jobs.getAllValues().stream().allMatch(j -> !j.getJobDataMap().containsKey("userId")
                && accountId.toString().equals(j.getJobDataMap().getString("accountId"))));
        assertEquals(Set.of(firstId.toString(),secondId.toString()),new HashSet<>(jobs.getAllValues().stream()
                .map(j -> j.getJobDataMap().getString("subscriptionId")).toList()));
    }
    @Test void renewingOneSubscriptionKeepsLegacyJobOfAnotherSubscription() throws Exception {
        prepareLegacy(); var scheduler = mock(Scheduler.class);
        var oldKey = JobKey.jobKey("sub_notify_DAYS_LEFT_1_10", "subscription_notifications");
        when(scheduler.checkExists(oldKey)).thenReturn(true);
        var old = JobBuilder.newJob(SubscriptionExpiryNotificationJob.class).withIdentity(oldKey)
                .usingJobData("userId", 10L).usingJobData("subscriptionId", firstId.toString()).build();
        when(scheduler.getJobDetail(oldKey)).thenReturn(old);
        var service = new SchedulerService(scheduler, subscriptions, legacy);
        service.scheduleSubscriptionNotifications(accountId, secondId, Instant.now().plusSeconds(10 * 86400));
        verify(scheduler, never()).deleteJob(oldKey);
        service.scheduleSubscriptionNotifications(accountId, firstId, Instant.now().plusSeconds(10 * 86400));
        verify(scheduler).deleteJob(oldKey);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void legacyJobSupportsNumericAndStringExpiration(boolean numeric) throws Exception {
        prepareLegacy(); Instant expiry=Instant.now().plusSeconds(86400);
        var data=new JobDataMap(); data.put("userId",10L);data.put("type","DAYS_LEFT_1");
        data.put("expireAt",numeric?expiry.toEpochMilli():expiry.toString());
        var detail=JobBuilder.newJob(SubscriptionExpiryNotificationJob.class).usingJobData(data).build();
        var context=mock(JobExecutionContext.class);when(context.getJobDetail()).thenReturn(detail);
        var reader=mock(ExternalSubscriptionService.class);
        when(reader.getExternalUser(accountId,firstId)).thenReturn(RemnawaveUser.builder().expireAt(expiry).build());
        var events=mock(ApplicationEventPublisher.class);
        new SubscriptionExpiryNotificationJob(new NotificationJobSupport(subscriptions,legacy),reader,events).execute(context);
        verify(events).publishEvent(any(SubscriptionExpiryDueEvent.class));
        verify(subscriptions).requireOwned(accountId,firstId);
    }
    @Test void schedulingRejectsForeignSubscriptionBeforeWritingJobs() {
        var scheduler=mock(Scheduler.class);
        when(subscriptions.requireOwned(accountId,secondId)).thenThrow(new IllegalArgumentException("Foreign subscription"));
        assertThrows(IllegalArgumentException.class, () -> new SchedulerService(scheduler,subscriptions,legacy)
                .scheduleSubscriptionNotifications(accountId,secondId,Instant.now()));
        verifyNoInteractions(scheduler);
    }
    @Test void newJobDoesNotNeedLegacyRecipient() {
        var data=new JobDataMap();data.put("accountId",accountId.toString());data.put("subscriptionId",secondId.toString());
        assertEquals(secondId,new NotificationJobSupport(subscriptions,legacy).target(data).subscriptionId());
        verifyNoInteractions(legacy,resolver);
    }
    @Test void oldJobWithExplicitSubscriptionChecksItsOwner() {
        prepareLegacy();var data=new JobDataMap();data.put("userId",10L);data.put("subscriptionId",secondId.toString());
        when(subscriptions.requireOwned(accountId,secondId)).thenThrow(new IllegalArgumentException("Foreign subscription"));
        assertThrows(IllegalArgumentException.class, () -> new NotificationJobSupport(subscriptions,legacy).target(data));
        verify(resolver,never()).subscriptionId(anyLong());
    }
}
