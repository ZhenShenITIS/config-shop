package tg.configshop.services;

import tg.configshop.exceptions.subscription.InsufficientBalanceException;
import tg.configshop.exceptions.traffic.TrafficPackageNotFoundException;
import tg.configshop.model.TrafficPackage;

import java.util.List;

public interface TrafficPackageService {
    List<Integer> getAvailableTrafficPackageSizes();

    long getTrafficPackageCostByGb(int trafficGb) throws TrafficPackageNotFoundException;

    List<TrafficPackage> getAvailableTrafficPackages();

    TrafficPackage getTrafficPackageByGb(int trafficGb) throws TrafficPackageNotFoundException;

    void buyTraffic(Long userId, int trafficGb) throws InsufficientBalanceException, TrafficPackageNotFoundException;
}
