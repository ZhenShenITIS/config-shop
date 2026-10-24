package tg.configshop.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tg.configshop.constants.TopUpSource;
import tg.configshop.model.TopUp;

public interface TopUpRepository extends JpaRepository<TopUp, UUID> {
    @Query("""
        SELECT COALESCE(SUM(t.value), 0)
        FROM TopUp t
        WHERE t.account.id = :accountId
          AND t.topUpSource = :source
    """)
    Long getSumByAccountIdAndSource(@Param("accountId") UUID accountId, @Param("source") TopUpSource source);
    boolean existsByPlatega_Id(java.util.UUID paymentId);
    boolean existsBySourcePayment_IdAndAccount_IdAndReferralLevel(java.util.UUID paymentId, java.util.UUID accountId, Short level);
}
