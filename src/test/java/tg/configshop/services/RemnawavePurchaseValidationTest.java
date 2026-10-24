package tg.configshop.services;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tg.configshop.external_api.remnawave.*;
import tg.configshop.external_api.remnawave.config.RemnawaveApiVersion;
import tg.configshop.model.*;
import tg.configshop.quartz.services.SchedulerService;
import tg.configshop.repositories.PurchaseRepository;
import tg.configshop.services.impl.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RemnawavePurchaseValidationTest {
    private final UserService users = mock(UserService.class);
    private final BalanceService balance = mock(BalanceService.class);
    private final PurchaseRepository purchases = mock(PurchaseRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final BotUser user = tg.configshop.TestUsers.user(736L, null, null);
    @ParameterizedTest @ValueSource(ints = {2, 3})
    void trafficChecksReferenceBeforeCharging(int version) {
        when(users.getUser(736L)).thenReturn(user);
        var service = new TrafficPackageServiceImpl(balance, users, events, new RemnawaveApiVersion(version));
        assertThrows(IllegalArgumentException.class, () -> service.buyTraffic(736L, 10));
        verifyNoInteractions(balance, purchases, events);
    }
    @ParameterizedTest @ValueSource(ints = {2, 3})
    void devicesCheckReferenceBeforeCharging(int version) {
        when(users.getUser(736L)).thenReturn(user);
        var devices = mock(DeviceService.class);
        var external = mock(ExternalSubscriptionService.class);
        var service = new DevicePurchaseService(devices, users, balance, mock(SubscriptionService.class), external, new RemnawaveApiVersion(version));
        assertThrows(IllegalArgumentException.class, () -> service.purchaseDevices(736L, 1));
        verifyNoInteractions(balance, devices);
    }
    @ParameterizedTest @ValueSource(ints = {2, 3})
    void subscriptionChecksReferenceBeforeCharging(int version) {
        when(users.getUser(736L)).thenReturn(user);
        var client = mock(RemnawaveClient.class);
        var scheduler = mock(SchedulerService.class);
        var service = new SubscriptionServiceImpl(balance, users, client, events, new RemnawaveApiVersion(version), scheduler);
        ReflectionTestUtils.setField(service, "MIN_DEVICE_COUNT", 1);
        ReflectionTestUtils.setField(service, "MAX_DEVICE_COUNT", 5);
        assertThrows(IllegalArgumentException.class, () -> service.buySubscription(736L, 30, 1));
        verifyNoInteractions(balance, purchases, events, scheduler, client);
    }
}
