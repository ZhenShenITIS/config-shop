package tg.configshop.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tg.configshop.constants.SubscriptionChangeStatus;
import tg.configshop.dto.DevicePurchasePreview;
import tg.configshop.exceptions.devices.TooManyDevicesException;
import tg.configshop.exceptions.subscription.SubscriptionChangeInProgressException;
import tg.configshop.util.DateUtil;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DevicePurchaseService {
    private final SubscriptionService subscriptionService;
    private final ExternalSubscriptionService externalSubscriptionService;
    private final SubscriptionChangeService changes;

    public DevicePurchasePreview options(UUID accountId, UUID subscriptionId) {
        return calculate(accountId, subscriptionId, 0);
    }

    public static long priceFor(long unitPrice, int deviceCount) {
        return Math.multiplyExact(unitPrice, deviceCount);
    }

    public DevicePurchasePreview preview(UUID accountId, UUID subscriptionId, int deviceCount) {
        requirePositive(deviceCount);
        return calculate(accountId, subscriptionId, deviceCount);
    }

    public long purchaseDevices(UUID accountId, UUID subscriptionId, int deviceCount) {
        requirePositive(deviceCount);
        var purchase = changes.buyDevices(accountId, subscriptionId, deviceCount,
                (expiresAt, currentDevices) -> quote(DateUtil.getDaysLeft(expiresAt), currentDevices, deviceCount).totalPrice());
        if (purchase.status() != SubscriptionChangeStatus.COMPLETED) throw new SubscriptionChangeInProgressException();
        return purchase.price();
    }

    private DevicePurchasePreview calculate(UUID accountId, UUID subscriptionId, int deviceCount) {
        var current = externalSubscriptionService.getExternalUser(accountId, subscriptionId);
        return quote(current.daysLeft(), current.hwidDeviceLimit(), deviceCount);
    }

    private DevicePurchasePreview quote(long days, int currentDevices, int deviceCount) {
        if (days <= 0) throw new IllegalStateException("Subscription is expired");
        int available = subscriptionService.getMaxDeviceCount() - currentDevices;
        if (deviceCount > 0 && deviceCount > available) throw new TooManyDevicesException("Maximum device count exceeded");
        long unit = days < 30 ? subscriptionService.getExtraPricePerDeviceByDays(30)
                : days < 90 ? subscriptionService.getExtraPricePerDeviceByDays(90)
                : days < 180 ? subscriptionService.getExtraPricePerDeviceByDays(180)
                : days < 360 ? subscriptionService.getExtraPricePerDeviceByDays(360)
                : 2L * subscriptionService.getExtraPricePerDeviceByDays(360);
        return new DevicePurchasePreview(unit, priceFor(unit, deviceCount), available, currentDevices);
    }

    private static void requirePositive(int devices) {
        if (devices <= 0) throw new IllegalArgumentException("Non-positive device count");
    }
}
