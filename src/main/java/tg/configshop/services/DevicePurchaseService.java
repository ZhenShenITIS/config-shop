package tg.configshop.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.constants.PurchaseType;
import tg.configshop.external_api.remnawave.config.RemnawaveApiVersion;
import tg.configshop.exceptions.devices.TooManyDevicesException;
import tg.configshop.exceptions.subscription.InsufficientBalanceException;
import tg.configshop.model.BotUser;
import tg.configshop.model.Purchase;

@Service
@RequiredArgsConstructor
public class DevicePurchaseService {
    private final DeviceService deviceService;
    private final UserService userService;
    private final BalanceService balanceService;
    private final SubscriptionService subscriptionService;
    private final ExternalSubscriptionService externalSubscriptionService;
    private final RemnawaveApiVersion apiVersion;


    @Transactional(rollbackFor = Exception.class)
    public long purchaseDevices(Long userId, int deviceCount)
            throws InsufficientBalanceException, TooManyDevicesException {
        BotUser user = userService.getUser(userId);
        if (deviceCount <= 0) throw new IllegalArgumentException("Non-positive device count");
        apiVersion.select(user.remnawaveRef());
        long daysLeft = externalSubscriptionService.getExternalUser(userId).daysLeft();
        if (daysLeft <= 0) throw new IllegalStateException("Subscription is expired");
        long unitPrice = daysLeft < 30 ? subscriptionService.getExtraPricePerDeviceByDays(30)
                : daysLeft < 90 ? subscriptionService.getExtraPricePerDeviceByDays(90)
                : daysLeft < 180 ? subscriptionService.getExtraPricePerDeviceByDays(180)
                : daysLeft < 360 ? subscriptionService.getExtraPricePerDeviceByDays(360)
                : 2L * subscriptionService.getExtraPricePerDeviceByDays(360);
        long totalPrice = Math.multiplyExact(unitPrice, deviceCount);
        balanceService.purchase(Purchase
                .builder()
                        .purchaseType(PurchaseType.DEVICE)
                        .account(user.getAccount())
                        .remnawaveSubscription(user.getSubscription())
                        .accountPriceRate(java.math.BigDecimal.ONE)
                        .paidAmount(totalPrice)
                        .deviceCount(deviceCount)
                .build());
        deviceService.addDeviceById(userId, deviceCount);
        return totalPrice;

    }
}
