package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tg.configshop.model.BotUser;
import tg.configshop.model.PromoCode;
import tg.configshop.model.PromoCodeUse;

public interface PromoCodeUseRepository extends JpaRepository<PromoCodeUse, Long> {
    boolean existsByPromoCodeAndBotUser(PromoCode promoCode, BotUser botUser);
    boolean existsByBotUserAndPromoCode_IsReferralTrue(BotUser botUser);
}
