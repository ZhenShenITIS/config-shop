package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tg.configshop.model.BotUser;
import tg.configshop.model.PromoCode;
import tg.configshop.model.PromoCodeUse;

import java.util.Optional;

public interface PromoCodeUseRepository extends JpaRepository<PromoCodeUse, Long> {
    boolean existsByPromoCodeAndBotUser(PromoCode promoCode, BotUser botUser);
    boolean existsByBotUserAndPromoCode_IsReferralTrue(BotUser botUser);

    @Query(value = """
    SELECT pcu.promoCode
    FROM PromoCodeUse pcu
    WHERE pcu.botUser.id = :userId
        AND pcu.promoCode.isReferral = true
    """)
    Optional<PromoCode> findReferralPromoUse(Long userId);
}
