package tg.configshop.services;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.constants.PurchaseType;
import tg.configshop.dto.DevicePurchasePreview;
import tg.configshop.exceptions.devices.TooManyDevicesException;
import tg.configshop.model.Purchase;
import java.math.BigDecimal;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class DevicePurchaseService {
    private final DeviceService deviceService;
    private final SubscriptionAccessService subscriptions;
    private final BalanceService balanceService;
    private final SubscriptionService subscriptionService;
    private final ExternalSubscriptionService externalSubscriptionService;

    public DevicePurchasePreview options(UUID accountId, UUID subscriptionId) { return calculate(accountId, subscriptionId, 0); }
    public static long priceFor(long unitPrice, int deviceCount) { return Math.multiplyExact(unitPrice, deviceCount); }
    public DevicePurchasePreview preview(UUID accountId, UUID subscriptionId, int deviceCount) {
        if (deviceCount <= 0) throw new IllegalArgumentException("Non-positive device count");
        return calculate(accountId, subscriptionId, deviceCount);
    }
    private DevicePurchasePreview calculate(UUID accountId, UUID subscriptionId, int deviceCount) {
        var current = externalSubscriptionService.getExternalUser(accountId, subscriptionId);
        long days = current.daysLeft();
        if (days <= 0) throw new IllegalStateException("Subscription is expired");
        int available = subscriptionService.getMaxDeviceCount() - current.hwidDeviceLimit();
        if (deviceCount > 0 && deviceCount > available) throw new TooManyDevicesException("Maximum device count exceeded");
        long unit = days < 30 ? subscriptionService.getExtraPricePerDeviceByDays(30)
                : days < 90 ? subscriptionService.getExtraPricePerDeviceByDays(90)
                : days < 180 ? subscriptionService.getExtraPricePerDeviceByDays(180)
                : days < 360 ? subscriptionService.getExtraPricePerDeviceByDays(360)
                : 2L * subscriptionService.getExtraPricePerDeviceByDays(360);
        return new DevicePurchasePreview(unit, priceFor(unit, deviceCount), available, current.hwidDeviceLimit());
    }
    @Transactional(rollbackFor = Exception.class)
    public long purchaseDevices(UUID accountId, UUID subscriptionId, int deviceCount) {
        var owned = subscriptions.requireOwned(accountId, subscriptionId);
        var preview = preview(accountId, subscriptionId, deviceCount);
        balanceService.purchase(Purchase.builder().purchaseType(PurchaseType.DEVICE).account(owned.getAccount())
                .remnawaveSubscription(owned).accountPriceRate(BigDecimal.ONE).paidAmount(preview.totalPrice())
                .deviceCount(deviceCount).build());
        deviceService.addDeviceById(accountId, subscriptionId, deviceCount);
        return preview.totalPrice();
    }
}
