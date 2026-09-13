package tg.configshop.services;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.config.RemnawaveApiVersion;
import tg.configshop.model.BotUser;
import tg.configshop.model.Subscription;
import tg.configshop.model.TrafficPackage;
import tg.configshop.quartz.services.SchedulerService;
import tg.configshop.repositories.PurchaseRepository;
import tg.configshop.repositories.SubscriptionRepository;
import tg.configshop.repositories.TrafficPackageRepository;
import tg.configshop.services.impl.SubscriptionServiceImpl;
import tg.configshop.services.impl.TrafficPackageServiceImpl;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemnawavePurchaseValidationTest {
    private final UserService users = mock(UserService.class);
    private final PurchaseRepository purchases = mock(PurchaseRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final BotUser user = BotUser.builder().id(736L).balance(1000L).build();

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void trafficPurchaseChecksIdentifierBeforeCharging(int version) throws Exception {
        when(users.getUser(736L)).thenReturn(user);
        var packages = mock(TrafficPackageRepository.class);
        when(packages.findByTrafficGb(10)).thenReturn(Optional.of(
                TrafficPackage.builder().cost(100L).trafficGb(10).build()));
        var service = new TrafficPackageServiceImpl(packages, users, purchases, events, new RemnawaveApiVersion(version));

        assertThrows(IllegalArgumentException.class, () -> service.buyTraffic(736L, 10));
        verify(users, never()).decreaseBalance(any(), any());
        verifyNoInteractions(purchases, events);
        assertEquals(1000L, user.getBalance());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void devicePurchaseChecksIdentifierBeforeCharging(int version) throws Exception {
        when(users.getUser(736L)).thenReturn(user);
        var devices = mock(DeviceService.class);
        var service = new DevicePurchaseService(devices, users, purchases, new RemnawaveApiVersion(version));
        assertThrows(IllegalArgumentException.class, () -> service.purchaseDevices(736L, 1, 100L));
        verify(users, never()).decreaseBalance(any(), any());
        verifyNoInteractions(purchases, devices);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void subscriptionChecksIdentifierBeforeChangingBalanceOrPublishingEvent(int version) {
        when(users.getUser(736L)).thenReturn(user);
        var subscriptions = mock(SubscriptionRepository.class);
        var client = mock(RemnawaveClient.class);
        var scheduler = mock(SchedulerService.class);
        when(subscriptions.findById(1L)).thenReturn(Optional.of(Subscription.builder().cost(100L).build()));
        var service = new SubscriptionServiceImpl(subscriptions, users, client, purchases, events,
                new RemnawaveApiVersion(version), scheduler);

        assertThrows(IllegalArgumentException.class, () -> service.buySubscription(736L, 1L));
        assertEquals(1000L, user.getBalance());
        verifyNoInteractions(purchases, events, scheduler, client);
    }
}
