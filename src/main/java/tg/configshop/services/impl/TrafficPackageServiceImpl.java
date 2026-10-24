package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.constants.PurchaseType;
import tg.configshop.events.TrafficPaidEvent;
import tg.configshop.external_api.remnawave.config.RemnawaveApiVersion;
import tg.configshop.exceptions.subscription.InsufficientBalanceException;
import tg.configshop.exceptions.traffic.TrafficPackageNotFoundException;
import tg.configshop.model.Purchase;
import tg.configshop.model.TrafficPackage;
import tg.configshop.services.BalanceService;
import tg.configshop.services.TrafficPackageService;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrafficPackageServiceImpl implements TrafficPackageService {
    private static final List<Integer> TRAFFIC_PACKAGE_SIZES = List.of(10, 30, 50, 100, 250, 500);

    private final BalanceService balanceService;
    private final tg.configshop.services.SubscriptionAccessService subscriptions;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final RemnawaveApiVersion apiVersion;

    @Value("${TRAFFIC_PACKAGE_10_PRICE}")
    private long trafficPackage10Price;

    @Value("${TRAFFIC_PACKAGE_30_PRICE}")
    private long trafficPackage30Price;

    @Value("${TRAFFIC_PACKAGE_50_PRICE}")
    private long trafficPackage50Price;

    @Value("${TRAFFIC_PACKAGE_100_PRICE}")
    private long trafficPackage100Price;

    @Value("${TRAFFIC_PACKAGE_250_PRICE}")
    private long trafficPackage250Price;

    @Value("${TRAFFIC_PACKAGE_500_PRICE}")
    private long trafficPackage500Price;

    @Override
    public List<Integer> getAvailableTrafficPackageSizes() {
        return TRAFFIC_PACKAGE_SIZES;
    }

    @Override
    public long getTrafficPackageCostByGb(int trafficGb) throws TrafficPackageNotFoundException {
        return switch (trafficGb) {
            case 10 -> trafficPackage10Price;
            case 30 -> trafficPackage30Price;
            case 50 -> trafficPackage50Price;
            case 100 -> trafficPackage100Price;
            case 250 -> trafficPackage250Price;
            case 500 -> trafficPackage500Price;
            default -> throw new TrafficPackageNotFoundException();
        };
    }

    @Override
    public List<TrafficPackage> getAvailableTrafficPackages() {
        return TRAFFIC_PACKAGE_SIZES.stream().map(this::getTrafficPackageByGb).toList();
    }

    @Override
    public TrafficPackage getTrafficPackageByGb(int trafficGb) throws TrafficPackageNotFoundException {
        return TrafficPackage.builder().trafficGb(trafficGb).cost(getTrafficPackageCostByGb(trafficGb))
                .name("Пакет трафика на " + trafficGb + " ГБ").build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void buyTraffic(java.util.UUID accountId, java.util.UUID subscriptionId, int trafficGb) throws InsufficientBalanceException, TrafficPackageNotFoundException {
        var owned = subscriptions.requireOwned(accountId, subscriptionId);
        TrafficPackage trafficPackage = getTrafficPackageByGb(trafficGb);

        apiVersion.select(owned.remnawaveRef());
        balanceService.purchase(Purchase.builder()
                .purchaseType(PurchaseType.TRAFFIC)
                .account(owned.getAccount())
                .remnawaveSubscription(owned)
                .accountPriceRate(java.math.BigDecimal.ONE)
                .paidAmount(trafficPackage.getCost())
                .trafficGb(trafficPackage.getTrafficGb())
                .build());
        applicationEventPublisher.publishEvent(new TrafficPaidEvent(accountId, subscriptionId, trafficPackage.getTrafficGb()));

        log.info("User {} bought {} GB traffic", accountId, trafficPackage.getTrafficGb());
    }
}
