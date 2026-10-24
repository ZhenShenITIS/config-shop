package tg.configshop.services;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tg.configshop.external_api.remnawave.*;
import tg.configshop.external_api.remnawave.config.RemnawaveApiVersion;
import tg.configshop.model.*;
import tg.configshop.quartz.services.SchedulerService;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RemnawavePurchaseValidationTest {
    private final SubscriptionAccessService subscriptions = mock(SubscriptionAccessService.class);
    private final BalanceService balance = mock(BalanceService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final UUID accountId = UUID.randomUUID(), subscriptionId = UUID.randomUUID();
    @ParameterizedTest @ValueSource(ints = {2,3})
    void trafficChecksReferenceBeforeCharging(int version) {
        var invalid = RemnawaveSubscription.builder().account(Account.builder().id(accountId).build()).id(subscriptionId).build();
        when(subscriptions.requireOwned(accountId, subscriptionId)).thenReturn(invalid);
        var service = new tg.configshop.services.impl.TrafficPackageServiceImpl(balance, subscriptions, events, new RemnawaveApiVersion(version));
        assertThrows(IllegalArgumentException.class, () -> service.buyTraffic(accountId, subscriptionId, 10));
        verifyNoInteractions(balance, events);
    }
    @ParameterizedTest @ValueSource(ints = {2,3})
    void devicesCheckReferenceBeforeCharging(int version) {
        when(subscriptions.requireOwned(accountId, subscriptionId)).thenThrow(new IllegalArgumentException("Invalid subscription"));
        var devices = mock(DeviceService.class);
        var service = new DevicePurchaseService(devices, subscriptions, balance, mock(SubscriptionService.class), mock(ExternalSubscriptionService.class));
        assertThrows(IllegalArgumentException.class, () -> service.purchaseDevices(accountId, subscriptionId, 1));
        verifyNoInteractions(balance, devices);
    }
    @ParameterizedTest @ValueSource(ints = {2,3})
    void subscriptionChecksReferenceBeforeCharging(int version) {
        var invalid = RemnawaveSubscription.builder().account(Account.builder().id(accountId).build()).id(subscriptionId).build();
        when(subscriptions.requireOwned(accountId, subscriptionId)).thenReturn(invalid);
        var client = mock(RemnawaveClient.class);
        var scheduler = mock(SchedulerService.class);
        var service = new tg.configshop.services.impl.SubscriptionServiceImpl(balance, subscriptions, client, events, new RemnawaveApiVersion(version), scheduler);
        ReflectionTestUtils.setField(service,"MIN_DEVICE_COUNT",1); ReflectionTestUtils.setField(service,"MAX_DEVICE_COUNT",5);
        assertThrows(IllegalArgumentException.class, () -> service.buySubscription(accountId, subscriptionId, 30, 1));
        verifyNoInteractions(balance, events, scheduler, client);
    }
}
