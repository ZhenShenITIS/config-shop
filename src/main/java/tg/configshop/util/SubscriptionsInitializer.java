package tg.configshop.util;



import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import tg.configshop.model.Subscription;
import tg.configshop.repositories.SubscriptionRepository;
import tg.configshop.services.SubscriptionService;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SubscriptionsInitializer implements CommandLineRunner {

    private final SubscriptionRepository subscriptionRepository;

    private final SubscriptionService subscriptionService;




    @Override
    public void run(String... args) {
        if (subscriptionRepository.count() > 0) {
            System.out.println("[Init] База данных уже содержит записи. Инициализация пропущена.");
            return;
        }

        System.out.println("[Init] Начинаю заполнение базы данных тарифами...");
        List<Subscription> subscriptions = new ArrayList<>();

        int[] monthsOptions = {1, 3, 6, 12};

        for (int months : monthsOptions) {
            int days = months * 30;
            for (int devices = subscriptionService.getMinDeviceCount(); devices <= subscriptionService.getMaxDeviceCount(); devices++) {
                Subscription sub = new Subscription();
                sub.setDurationDays(days);
                sub.setDeviceCount(devices);
                sub.setTrafficLimitGb(devices * subscriptionService.getTrafficLimitPerDevice());
                String periodName = switch (months) {
                    case 1 -> "1 месяц";
                    case 12 -> "1 год";
                    case 6 -> "6 месяцев";
                    default -> months + " месяца";
                };
                sub.setName(String.format("%s (%d устр.)", periodName, devices));

                long cost = calculateCost(days, devices);
                sub.setCost(cost);

                sub.setDescription("VPN подписка на %s дней и %s устройств".formatted(days, devices));

                subscriptions.add(sub);
            }
        }

        subscriptionRepository.saveAll(subscriptions);
        System.out.println("[Init] Готово! Добавлено " + subscriptions.size() + " тарифов.");
    }

    private long calculateCost(int days, int devices) {
        long basePrice = subscriptionService.getBaseSubscriptionCostByDays(days);
        long extraPerDevice = subscriptionService.getExtraPricePerDeviceByDays(days);
        int extraDevices = devices - subscriptionService.getMinDeviceCount();
        if (extraDevices < 0) extraDevices = 0;
        return basePrice + (extraDevices * extraPerDevice);
    }
}
