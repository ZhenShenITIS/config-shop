package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tg.configshop.model.Account;
import java.util.UUID;
import tg.configshop.model.PromoCode;
import tg.configshop.model.PromoCodeUse;

import java.util.Optional;

public interface PromoCodeUseRepository extends JpaRepository<PromoCodeUse, UUID> {
    boolean existsByPromoCodeAndAccount(PromoCode promoCode, Account account);
    boolean existsByAccountAndPromoCode_IsReferralTrue(Account account);

    @Query(value = """
    SELECT pcu.promoCode
    FROM PromoCodeUse pcu
    WHERE pcu.account.id = :userId
        AND pcu.promoCode.isReferral = true
    """)
    Optional<PromoCode> findReferralPromoUse(UUID userId);
}
