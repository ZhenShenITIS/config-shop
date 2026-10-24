package tg.configshop.services;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import tg.configshop.TestUsers;
import tg.configshop.dto.RemnawaveUser;
import tg.configshop.external_api.remnawave.config.RemnawaveApiVersion;
import tg.configshop.model.Purchase;
import tg.configshop.constants.PurchaseType;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyDevicePurchaseTest {
    @ParameterizedTest
    @CsvSource({"1,10", "29,10", "30,30", "89,30", "90,60", "179,60", "180,120", "359,120", "360,240", "720,240"})
    void preservesExistingPriceBandsAndRecordsNewDevicePurchases(long days, long unitPrice) throws Exception {
        var user = TestUsers.user(10, "uuid", 123L);
        var users = mock(UserService.class);
        when(users.getUser(10L)).thenReturn(user);
        var prices = mock(SubscriptionService.class);
        when(prices.getExtraPricePerDeviceByDays(30)).thenReturn(10);
        when(prices.getExtraPricePerDeviceByDays(90)).thenReturn(30);
        when(prices.getExtraPricePerDeviceByDays(180)).thenReturn(60);
        when(prices.getExtraPricePerDeviceByDays(360)).thenReturn(120);
        var panel = mock(ExternalSubscriptionService.class);
        when(panel.getExternalUser(10L)).thenReturn(RemnawaveUser.builder().daysLeft(days).build());
        var devices = mock(DeviceService.class);
        var balance = mock(BalanceService.class);
        var service = new DevicePurchaseService(devices, users, balance, prices, panel, new RemnawaveApiVersion(3));
        assertEquals(2 * unitPrice, service.purchaseDevices(10L, 2));
        var purchase = ArgumentCaptor.forClass(Purchase.class);
        verify(balance).purchase(purchase.capture());
        assertEquals(PurchaseType.DEVICE, purchase.getValue().getPurchaseType());
        assertEquals(user.getSubscription(), purchase.getValue().getRemnawaveSubscription());
        assertEquals(2 * unitPrice, purchase.getValue().getPaidAmount());
        verify(devices).addDeviceById(10L, 2);
    }
}
