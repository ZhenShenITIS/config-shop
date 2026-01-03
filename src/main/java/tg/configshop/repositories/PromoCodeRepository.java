package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tg.configshop.model.BotUser;
import tg.configshop.model.PromoCode;

import java.util.List;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {
    List<PromoCode> findAllByReferrer(BotUser referrer);
}
