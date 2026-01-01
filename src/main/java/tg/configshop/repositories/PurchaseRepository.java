package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tg.configshop.model.Purchase;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
}
