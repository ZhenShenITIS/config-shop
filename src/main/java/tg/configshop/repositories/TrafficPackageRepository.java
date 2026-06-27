package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tg.configshop.model.TrafficPackage;

import java.util.List;
import java.util.Optional;

public interface TrafficPackageRepository extends JpaRepository<TrafficPackage, Long> {
    Optional<TrafficPackage> findByTrafficGb(Integer trafficGb);

    List<TrafficPackage> findAllByOrderByTrafficGbAsc();
}
