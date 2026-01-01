package tg.configshop.util;



import lombok.AllArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import tg.configshop.model.Subscription;
import tg.configshop.repositories.SubscriptionRepository;

import java.util.ArrayList;
import java.util.List;

@Component
@AllArgsConstructor
public class SubscriptionsInitializer implements CommandLineRunner {

    private final SubscriptionRepository subscriptionRepository;

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
            for (int devices = 2; devices <= 11; devices++) {
                Subscription sub = new Subscription();
                sub.setDurationDays(days);
                sub.setDeviceCount(devices);
                sub.setTrafficLimitGb(devices * 300);
                String periodName = switch (months) {
                    case 1 -> "1 месяц";
                    case 12 -> "1 год";
                    case 6 -> "6 месяцев";
                    default -> months + " месяца";
                };
                sub.setName(String.format("%s (%d устр.)", periodName, devices));

                long cost = calculateCost(months, devices);
                sub.setCost(cost);

                sub.setDescription("VPN подписка на %s дней и %s устройств".formatted(days, devices));

                subscriptions.add(sub);
            }
        }

        subscriptionRepository.saveAll(subscriptions);
        System.out.println("[Init] Готово! Добавлено " + subscriptions.size() + " тарифов.");
    }

    private long calculateCost(int months, int devices) {
        long basePriceForTwo;
        long extraPerDevice;

        switch (months) {
            case 1 ->  { basePriceForTwo = 180;  extraPerDevice = 50; }
            case 3 ->  { basePriceForTwo = 420;  extraPerDevice = 100; }
            case 6 ->  { basePriceForTwo = 700;  extraPerDevice = 150; }
            case 12 -> { basePriceForTwo = 1200; extraPerDevice = 200; }
            default -> throw new IllegalArgumentException("Неизвестный период: " + months);
        }

        int extraDevices = devices - 2;
        if (extraDevices < 0) extraDevices = 0;
        return basePriceForTwo + (extraDevices * extraPerDevice);
    }
}
