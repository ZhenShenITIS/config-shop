package tg.configshop.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.model.Subscription;
import tg.configshop.repositories.SubscriptionRepository;
import tg.configshop.services.SubscriptionService;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionsInitializer implements CommandLineRunner {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[Init] Запуск синхронизации тарифов...");

        int[] monthsOptions = {1, 3, 6, 12};

        int createdCount = 0;
        int updatedCount = 0;

        for (int months : monthsOptions) {
            int days = months * 30;
            for (int devices = subscriptionService.getMinDeviceCount(); devices <= subscriptionService.getMaxDeviceCount(); devices++) {

                long calculatedCost = calculateCost(days, devices);
                long calculatedTraffic = (long) devices * subscriptionService.getTrafficLimitPerDevice();
                String name = generateName(months, devices);
                String description = "Подписка на %s дней и %s устройств".formatted(days, devices);

                Optional<Subscription> existingSubOpt = subscriptionRepository.findByDurationDaysAndDeviceCount(days, devices);

                if (existingSubOpt.isPresent()) {

                    Subscription existing = existingSubOpt.get();
                    boolean needUpdate = false;

                    if (existing.getCost() != calculatedCost) {
                        log.info("Обновление цены для [{} дней, {} устр]: {} -> {}", days, devices, existing.getCost(), calculatedCost);
                        existing.setCost(calculatedCost);
                        needUpdate = true;
                    }

                    if (existing.getTrafficLimitGb() != calculatedTraffic) {
                        existing.setTrafficLimitGb((int) calculatedTraffic);
                        needUpdate = true;
                    }

                    if (!existing.getName().equals(name)) {
                        existing.setName(name);
                        needUpdate = true;
                    }

                    if (needUpdate) {
                        subscriptionRepository.save(existing);
                        updatedCount++;
                    }

                } else {

                    Subscription sub = new Subscription();
                    sub.setDurationDays(days);
                    sub.setDeviceCount(devices);
                    sub.setTrafficLimitGb((int) calculatedTraffic);
                    sub.setName(name);
                    sub.setCost(calculatedCost);
                    sub.setDescription(description);

                    subscriptionRepository.save(sub);
                    createdCount++;
                }
            }
        }

        log.info("[Init] Синхронизация завершена. Создано: {}, Обновлено: {}", createdCount, updatedCount);
    }

    private String generateName(int months, int devices) {
        String periodName = switch (months) {
            case 1 -> "1 месяц";
            case 6 -> "6 месяцев";
            case 12 -> "1 год";
            default -> months + " месяца";
        };
        return String.format("%s (%d устр.)", periodName, devices);
    }

    private long calculateCost(int days, int devices) {
        long basePrice = subscriptionService.getBaseSubscriptionCostByDays(days);
        long extraPerDevice = subscriptionService.getExtraPricePerDeviceByDays(days);
        int extraDevices = Math.max(0, devices - subscriptionService.getMinDeviceCount());
        return basePrice + (extraDevices * extraPerDevice);
    }
}
