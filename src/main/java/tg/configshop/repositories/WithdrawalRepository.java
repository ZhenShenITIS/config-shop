package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tg.configshop.model.Withdrawal;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, UUID> {
    @Query("""
    SELECT COALESCE(SUM(w.amount), 0)
    FROM Withdrawal w
    WHERE w.account.id = :accountId
      AND w.status IN ('DONE', 'IN_PROGRESS')
    """)
    Long sumConsumedReferralBalance(@Param("accountId") UUID accountId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select w from Withdrawal w where w.id = :id")
    Optional<Withdrawal> findByIdWithLock(UUID id);
}
