package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tg.configshop.dto.SubscriptionChangeResult;
import tg.configshop.exceptions.subscription.InsufficientBalanceException;
import tg.configshop.exceptions.subscription.SubscriptionChangeInProgressException;
import tg.configshop.model.Subscription;
import tg.configshop.services.SubscriptionChangeService;
import tg.configshop.services.SubscriptionPricing;
import tg.configshop.services.SubscriptionService;

import java.util.UUID;


@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {
    private final SubscriptionChangeService changes;

    private final SubscriptionPricing pricing;

    @Override
    public void buySubscription(UUID accountId, UUID subscriptionId, int days, int devices) {
        var purchase = renewSubscription(UUID.randomUUID(), accountId, subscriptionId, days, devices);
        switch (purchase.status()) {
            case COMPLETED -> { }
            case INSUFFICIENT_BALANCE -> throw new InsufficientBalanceException();
            default -> throw new SubscriptionChangeInProgressException();
        }
    }

    @Override
    public SubscriptionChangeResult renewSubscription(UUID id, UUID accountId, UUID subscriptionId, int days, int devices) {
        return changes.renew(id, accountId, subscriptionId, days, devices);
    }

    @Override
    public Subscription getSubscriptionByDaysAndDevices(int days, int devices) {
        return pricing.getSubscriptionByDaysAndDevices(days, devices);
    }

    @Override
    public int getBaseSubscriptionCostByDays(int days) {
        return pricing.getBaseSubscriptionCostByDays(days);
    }

    @Override
    public int getMinDeviceCount() {
        return pricing.getMinDeviceCount();
    }

    @Override
    public int getMaxDeviceCount() {
        return pricing.getMaxDeviceCount();
    }

    @Override
    public int getExtraPricePerDeviceByDays(int days) {
        return pricing.getExtraPricePerDeviceByDays(days);
    }

    @Override
    public int getTrafficLimitPerDevice() {
        return pricing.getTrafficLimitPerDevice();
    }
}
