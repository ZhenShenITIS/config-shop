package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.events.SubscriptionPaidEvent;
import tg.configshop.exceptions.subscription.InsufficientBalanceException;
import tg.configshop.exceptions.subscription.SubscriptionNotFoundException;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.config.RemnawaveApiVersion;
import tg.configshop.model.Purchase;
import tg.configshop.model.Subscription;
import tg.configshop.quartz.services.SchedulerService;
import tg.configshop.services.BalanceService;
import tg.configshop.services.SubscriptionService;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {
    private final BalanceService balanceService;
    private final tg.configshop.services.SubscriptionAccessService subscriptions;
    private final RemnawaveClient remnawaveClient;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final RemnawaveApiVersion apiVersion;
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


    private final SchedulerService schedulerService;

    @Override
    @Transactional
    public void buySubscription(java.util.UUID accountId, java.util.UUID subscriptionId, int days, int devices) throws InsufficientBalanceException {
        var owned = subscriptions.requireOwned(accountId, subscriptionId);
        Subscription subscription = getSubscriptionByDaysAndDevices(days, devices);
        apiVersion.select(owned.remnawaveRef());
        Instant currentExpiry = remnawaveClient.getUser(owned.remnawaveRef()).expireAt();
        Instant now = Instant.now();
        Instant newExpired = (currentExpiry.isAfter(now) ? currentExpiry : now)
                .plusSeconds(days * 86400L);
        balanceService.purchase(Purchase.builder().account(owned.getAccount())
                .remnawaveSubscription(owned).paidAmount(subscription.getCost())
                .accountPriceRate(java.math.BigDecimal.ONE)
                .subDays(days).subDevices(devices).build());

        applicationEventPublisher.publishEvent(new SubscriptionPaidEvent(accountId, subscriptionId, subscription.getDeviceCount(), newExpired));



        schedulerService.scheduleSubscriptionNotifications(accountId, subscriptionId, newExpired);


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
