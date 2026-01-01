package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tg.configshop.model.PromoCode;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {
}
