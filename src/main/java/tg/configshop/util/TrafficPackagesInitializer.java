package tg.configshop.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.model.TrafficPackage;
import tg.configshop.repositories.TrafficPackageRepository;
import tg.configshop.services.TrafficPackageService;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrafficPackagesInitializer implements CommandLineRunner {

    private final TrafficPackageRepository trafficPackageRepository;
    private final TrafficPackageService trafficPackageService;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[Init] Запуск синхронизации пакетов трафика...");

        int createdCount = 0;
        int updatedCount = 0;

        for (int trafficGb : trafficPackageService.getAvailableTrafficPackageSizes()) {
            long calculatedCost = trafficPackageService.getTrafficPackageCostByGb(trafficGb);
            String name = generateName(trafficGb);
            String description = "Пакет трафика на %s ГБ".formatted(trafficGb);

            Optional<TrafficPackage> existingPackageOpt = trafficPackageRepository.findByTrafficGb(trafficGb);

            if (existingPackageOpt.isPresent()) {
                TrafficPackage existing = existingPackageOpt.get();
                boolean needUpdate = false;

                if (!existing.getCost().equals(calculatedCost)) {
                    log.info("Обновление цены для пакета [{} ГБ]: {} -> {}", trafficGb, existing.getCost(), calculatedCost);
                    existing.setCost(calculatedCost);
                    needUpdate = true;
                }

                if (!existing.getName().equals(name)) {
                    existing.setName(name);
                    needUpdate = true;
                }

                if (!description.equals(existing.getDescription())) {
                    existing.setDescription(description);
                    needUpdate = true;
                }

                if (needUpdate) {
                    trafficPackageRepository.save(existing);
                    updatedCount++;
                }
            } else {
                trafficPackageRepository.save(TrafficPackage.builder()
                        .trafficGb(trafficGb)
                        .cost(calculatedCost)
                        .name(name)
                        .description(description)
                        .build());
                createdCount++;
            }
        }

        log.info("[Init] Синхронизация пакетов трафика завершена. Создано: {}, Обновлено: {}", createdCount, updatedCount);
    }

    private String generateName(int trafficGb) {
        return "%d ГБ".formatted(trafficGb);
    }
}
