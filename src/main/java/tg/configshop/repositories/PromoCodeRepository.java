package tg.configshop.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import tg.configshop.model.BotUser;
import tg.configshop.model.PromoCode;

import java.util.List;
import java.util.Optional;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {
    List<PromoCode> findAllByReferrer(BotUser referrer);

    Optional<PromoCode> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PromoCode p WHERE p.code = :code")
    Optional<PromoCode> findByCodeWithLock(String code);

    @Query("SELECT p.code FROM PromoCode p WHERE p.referrer = :referrer")
    List<String> findCodesByReferrer(BotUser referrer);
}
