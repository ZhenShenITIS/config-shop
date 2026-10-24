package tg.configshop.services;

import tg.configshop.exceptions.subscription.InsufficientBalanceException;
import tg.configshop.exceptions.subscription.SubscriptionNotFoundException;
import tg.configshop.model.Subscription;

public interface SubscriptionService {
    default tg.configshop.dto.SubscriptionPurchasePreview previewPurchase(int days, int devices) {
        var offer = getSubscriptionByDaysAndDevices(days, devices);
        long base = getBaseSubscriptionCostByDays(days);
        return new tg.configshop.dto.SubscriptionPurchasePreview(base, Math.max(0, devices - getMinDeviceCount()),
                offer.getCost() - base, offer.getCost());
    }
    int getBaseSubscriptionCostByDays (int days) throws SubscriptionNotFoundException;
    int getMinDeviceCount ();
    int getMaxDeviceCount ();
    int getExtraPricePerDeviceByDays (int days) throws SubscriptionNotFoundException;
    int getTrafficLimitPerDevice ();
    void buySubscription(java.util.UUID accountId, java.util.UUID subscriptionId, int days, int devices) throws InsufficientBalanceException;
    Subscription getSubscriptionByDaysAndDevices (int days, int devices) throws SubscriptionNotFoundException;
}
