package tg.configshop.services;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tg.configshop.TestUsers;
import tg.configshop.dto.RemnawaveUser;
import tg.configshop.constants.SubscriptionChangeStatus;
import tg.configshop.dto.SubscriptionChangeResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.ToLongBiFunction;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyDevicePurchaseTest {
    @ParameterizedTest
    @CsvSource({"1,10", "29,10", "30,30", "89,30", "90,60", "179,60", "180,120", "359,120", "360,240", "720,240"})
    void preservesExistingPriceBandsForPreviewAndPurchase(long days, long unitPrice) {
        var user = TestUsers.user(10, "uuid", 123L);
        var accountId = user.getAccount().getId(); var subscriptionId = user.getSubscription().getId();
        var prices = mock(SubscriptionService.class);
        when(prices.getExtraPricePerDeviceByDays(30)).thenReturn(10);
        when(prices.getExtraPricePerDeviceByDays(90)).thenReturn(30);
        when(prices.getExtraPricePerDeviceByDays(180)).thenReturn(60);
        when(prices.getExtraPricePerDeviceByDays(360)).thenReturn(120);
        when(prices.getMaxDeviceCount()).thenReturn(10);
        var panel = mock(ExternalSubscriptionService.class);
        when(panel.getExternalUser(accountId, subscriptionId)).thenReturn(RemnawaveUser.builder().daysLeft(days).hwidDeviceLimit(1).build());
        var changes = mock(SubscriptionChangeService.class);
        when(changes.buyDevices(eq(accountId), eq(subscriptionId), eq(2), any())).thenAnswer(invocation -> {
            ToLongBiFunction<Instant, Integer> quote = invocation.getArgument(3);
            var expiry = Instant.now().plus(days, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS);
            return new SubscriptionChangeResult(UUID.randomUUID(), quote.applyAsLong(expiry, 1),
                    SubscriptionChangeStatus.COMPLETED);
        });
        var service = new DevicePurchaseService(prices, panel, changes);
        assertEquals(2 * unitPrice, service.preview(accountId, subscriptionId, 2).totalPrice());
        assertEquals(2 * unitPrice, service.purchaseDevices(accountId, subscriptionId, 2));
    }
}
