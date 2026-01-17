package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import org.quartz.Scheduler;
import org.quartz.spi.JobFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.exceptions.subscription.InsufficientBalanceException;
import tg.configshop.exceptions.subscription.SubscriptionNotFoundException;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.model.BotUser;
import tg.configshop.model.Purchase;
import tg.configshop.model.Subscription;
import tg.configshop.quartz.services.SchedulerService;
import tg.configshop.repositories.PurchaseRepository;
import tg.configshop.repositories.SubscriptionRepository;
import tg.configshop.services.SubscriptionService;
import tg.configshop.services.UserService;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {
    private final SubscriptionRepository subscriptionRepository;
    private final UserService userService;
    private final RemnawaveClient remnawaveClient;
    private final PurchaseRepository purchaseRepository;
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
    public void buySubscription(Long userId, Long subscriptionId) throws InsufficientBalanceException {
        BotUser botUser = userService.getUser(userId);
        Subscription subscription = subscriptionRepository.findById(subscriptionId).orElseThrow();
        checkBalance(botUser, subscription);

        Instant newExpired = getNewExpired(botUser, subscription);

        updateUser(botUser, subscription, newExpired);

        updateSubscription(botUser, subscription, newExpired);

        savePurchase(botUser, subscription);

        schedulerService.scheduleSubscriptionNotifications(botUser.getId(), newExpired);


    }

    private void checkBalance(BotUser botUser, Subscription subscription) {
        if (botUser.getBalance() < subscription.getCost()) {
            throw new InsufficientBalanceException();
        }
    }

    private void updateUser (BotUser botUser, Subscription subscription, Instant newExpired) {
        botUser.setBalance(botUser.getBalance() - subscription.getCost());
        botUser.setExpireAt(newExpired);
    }

    private Instant getNewExpired (BotUser botUser, Subscription subscription) {
        long durationSeconds = subscription.getDurationDays() * 24 * 60 * 60;
        if (botUser.getExpireAt().isBefore(Instant.now())) {
            return Instant.now().plusSeconds(durationSeconds);
        } else {
            return botUser.getExpireAt().plusSeconds(durationSeconds);
        }
    }

    private void updateSubscription (BotUser botUser, Subscription subscription, Instant newExpired) {
        remnawaveClient.updateSubscription(botUser.getRemnawaveUuid(), newExpired, subscription.getTrafficLimitGb() * 1024L * 1024 * 1024, subscription.getDeviceCount());
    }

    private void savePurchase(BotUser botUser, Subscription subscription) {
        purchaseRepository.save(Purchase.builder()
                .botUser(botUser)
                .subscription(subscription)
                .createdAt(Instant.now())
                .paidAmount(subscription.getCost())
                .build());
    }

    @Override
    public Subscription getSubscriptionByDaysAndDevices(int days, int devices) throws SubscriptionNotFoundException {
        return subscriptionRepository.findByDurationDaysAndDeviceCount(days, devices).orElseThrow(SubscriptionNotFoundException::new);
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
