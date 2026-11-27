package tg.configshop.services;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import tg.configshop.repositories.AccountRepository;
import tg.configshop.repositories.SubscriptionChangeRepository;
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
        var invalid = RemnawaveSubscription.builder().account(Account.builder().id(accountId).build()).id(subscriptionId).build();
        when(subscriptions.requireOwned(accountId, subscriptionId)).thenReturn(invalid);
        var client = mock(RemnawaveClient.class);
        var scheduler = mock(SchedulerService.class);
        var service = new DevicePurchaseService(mock(SubscriptionService.class), mock(ExternalSubscriptionService.class),
                changes(client, version, scheduler));
        assertThrows(IllegalArgumentException.class, () -> service.purchaseDevices(accountId, subscriptionId, 1));
        verifyNoInteractions(balance, client, scheduler);
    }

    @ParameterizedTest @ValueSource(ints = {2,3})
    void subscriptionChecksReferenceBeforeCharging(int version) {
        var invalid = RemnawaveSubscription.builder().account(Account.builder().id(accountId).build()).id(subscriptionId).build();
        when(subscriptions.requireOwned(accountId, subscriptionId)).thenReturn(invalid);
        var client = mock(RemnawaveClient.class);
        var scheduler = mock(SchedulerService.class);
        var service = new tg.configshop.services.impl.SubscriptionServiceImpl(changes(client, version, scheduler),
                mock(SubscriptionPricing.class));
        assertThrows(IllegalArgumentException.class, () -> service.buySubscription(accountId, subscriptionId, 30, 1));
        verifyNoInteractions(balance, scheduler, client);
    }

    private SubscriptionChangeService changes(RemnawaveClient client, int version, SchedulerService scheduler) {
        return new SubscriptionChangeService(mock(PlatformTransactionManager.class), mock(SubscriptionChangeRepository.class),
                subscriptions, mock(AccountRepository.class), balance, mock(SubscriptionPricing.class),
                client, new RemnawaveApiVersion(version), scheduler);
    }
}
