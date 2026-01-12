package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tg.configshop.model.Withdrawal;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {
    @Query("""
    SELECT COALESCE(SUM(w.amount), 0) 
    FROM Withdrawal w 
    WHERE w.botUser.id = :userId 
      AND w.status IN ('DONE', 'IN_PROGRESS')
    """)
    Long sumConsumedReferralBalance(@Param("userId") Long userId);
}
