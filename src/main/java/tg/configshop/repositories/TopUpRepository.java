package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tg.configshop.model.TopUp;

public interface TopUpRepository extends JpaRepository<TopUp, Long> {
}
