package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tg.configshop.dto.SubscriptionChangeResult;
import tg.configshop.exceptions.subscription.InsufficientBalanceException;
import tg.configshop.exceptions.subscription.SubscriptionChangeInProgressException;
import tg.configshop.exceptions.subscription.SubscriptionNotFoundException;
import tg.configshop.model.Subscription;
import tg.configshop.services.SubscriptionService;
import tg.configshop.services.SubscriptionChangeService;

import java.util.UUID;


@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {
    private final SubscriptionChangeService changes;

    @Value("${MIN_DEVICE_COUNT}")
    private int MIN_DEVICE_COUNT;

    @Value("${MAX_DEVICE_COUNT}")
    private int MAX_DEVICE_COUNT;

    @Value("${BASE_PRICE_FOR_1_MONTH}")
    private int BASE_PRICE_FOR_1_MONTH;

    @Value("${BASE_PRICE_FOR_3_MONTH}")
    private int BASE_PRICE_FOR_3_MONTH;

    @Value("${BASE_PRICE_FOR_6_MONTH}")
    private int BASE_PRICE_FOR_6_MONTH;

    @Value("${BASE_PRICE_FOR_12_MONTH}")
    private int BASE_PRICE_FOR_12_MONTH;

    @Value("${EXTRA_PER_DEVICE_1_MONTH}")
    private int EXTRA_PER_DEVICE_1_MONTH;

    @Value("${EXTRA_PER_DEVICE_3_MONTH}")
    private int EXTRA_PER_DEVICE_3_MONTH;

    @Value("${EXTRA_PER_DEVICE_6_MONTH}")
    private int EXTRA_PER_DEVICE_6_MONTH;

    @Value("${EXTRA_PER_DEVICE_12_MONTH}")
    private int EXTRA_PER_DEVICE_12_MONTH;

    @Value("${TRAFFIC_LIMIT_PER_DEVICE}")
    private int TRAFFIC_LIMIT_PER_DEVICE;


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
        return changes.renew(id, accountId, subscriptionId, days, devices,
                () -> getSubscriptionByDaysAndDevices(days, devices).getCost());
    }

    @Override
    public Subscription getSubscriptionByDaysAndDevices(int days, int devices) throws SubscriptionNotFoundException {
        if (devices < getMinDeviceCount() || devices > getMaxDeviceCount()) throw new SubscriptionNotFoundException();
        long cost = getBaseSubscriptionCostByDays(days)
                + (long) Math.max(0, devices - getMinDeviceCount()) * getExtraPricePerDeviceByDays(days);
        return Subscription.builder().durationDays(days).deviceCount(devices).cost(cost)
                .trafficLimitGb(devices * getTrafficLimitPerDevice())
                .name("Подписка на " + days + " дней и " + devices + " устройств").build();
    }

    @Override
    public int getBaseSubscriptionCostByDays(int days) {
        return switch (days) {
            case 30 -> BASE_PRICE_FOR_1_MONTH;
            case 90 -> BASE_PRICE_FOR_3_MONTH;
            case 180 -> BASE_PRICE_FOR_6_MONTH;
            case 360 -> BASE_PRICE_FOR_12_MONTH;
            default -> throw new SubscriptionNotFoundException(
                    "Базовая стоимость для периода в " + days + " дней не найдена в конфигурации."
            );
        };
    }

    @Override
    public int getMinDeviceCount() {
        return MIN_DEVICE_COUNT;
    }

    @Override
    public int getMaxDeviceCount() {
        return MAX_DEVICE_COUNT;
    }

    @Override
    public int getExtraPricePerDeviceByDays(int days) {
        return switch (days) {
            case 30 -> EXTRA_PER_DEVICE_1_MONTH;
            case 90 -> EXTRA_PER_DEVICE_3_MONTH;
            case 180 -> EXTRA_PER_DEVICE_6_MONTH;
            case 360 -> EXTRA_PER_DEVICE_12_MONTH;
            default -> throw new SubscriptionNotFoundException(
                    "Стоимость доп. устройства для периода в " + days + " дней не найдена в конфигурации."
            );
        };
    }

    @Override
    public int getTrafficLimitPerDevice() {
        return TRAFFIC_LIMIT_PER_DEVICE;
    }
}
