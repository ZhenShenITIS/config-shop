package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tg.configshop.model.PromoCodeUse;

public interface PromoCodeUseRepository extends JpaRepository<PromoCodeUse, Long> {
}
