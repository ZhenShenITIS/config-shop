package tg.configshop.services;

import tg.configshop.dto.SubscriptionChangeResult;
import tg.configshop.dto.SubscriptionPurchasePreview;
import tg.configshop.exceptions.subscription.InsufficientBalanceException;
import tg.configshop.exceptions.subscription.SubscriptionNotFoundException;
import tg.configshop.model.Subscription;

import java.util.UUID;

public interface SubscriptionService {
    default SubscriptionPurchasePreview previewPurchase(int days, int devices) {
        var offer = getSubscriptionByDaysAndDevices(days, devices);
        long base = getBaseSubscriptionCostByDays(days);
        return new SubscriptionPurchasePreview(base, Math.max(0, devices - getMinDeviceCount()),
                offer.getCost() - base, offer.getCost());
    }
    int getBaseSubscriptionCostByDays (int days) throws SubscriptionNotFoundException;
    int getMinDeviceCount ();
    int getMaxDeviceCount ();
    int getExtraPricePerDeviceByDays (int days) throws SubscriptionNotFoundException;
    int getTrafficLimitPerDevice ();
    void buySubscription(UUID accountId, UUID subscriptionId, int days, int devices) throws InsufficientBalanceException;
    SubscriptionChangeResult renewSubscription(UUID id, UUID accountId, UUID subscriptionId, int days, int devices);
    Subscription getSubscriptionByDaysAndDevices (int days, int devices) throws SubscriptionNotFoundException;
}
