package tg.configshop.services;

import tg.configshop.exceptions.subscription.InsufficientBalanceException;
import tg.configshop.exceptions.subscription.SubscriptionNotFoundException;
import tg.configshop.model.Subscription;

public interface SubscriptionService {
    int getBaseSubscriptionCostByDays (int days) throws SubscriptionNotFoundException;
    int getMinDeviceCount ();
    int getMaxDeviceCount ();
    int getExtraPricePerDeviceByDays (int days) throws SubscriptionNotFoundException;
    int getTrafficLimitPerDevice ();
    void buySubscription(Long userId, Long subscriptionId) throws InsufficientBalanceException;
    Subscription getSubscriptionByDaysAndDevices (int days, int devices) throws SubscriptionNotFoundException;
}
