package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tg.configshop.constants.TopUpSource;
import tg.configshop.model.BotUser;
import tg.configshop.model.Referral;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReferralRepository extends JpaRepository<Referral, Long> {
    int countReferralsByReferrer_Id(Long referrerId);

    @Query("""
    SELECT COUNT(r) 
    FROM Referral r 
    WHERE r.referrer.id = :referrerId 
      AND r.referral.expireAt > :now
""")
    int countActiveReferrals(@Param("referrerId") Long referrerId, @Param("now") Instant now);

    List<BotUser> findAllByReferrer(BotUser referrer);

    Optional<Referral> findByReferral_Id(Long referralId);

    @Query(value = """
        WITH RECURSIVE ancestors AS (
            SELECT referrer_id
            FROM referrals
            WHERE referral_id = :potentialReferrerId
            
            UNION ALL
            
            SELECT r.referrer_id
            FROM referrals r
            INNER JOIN ancestors a ON r.referral_id = a.referrer_id
        )
        SELECT COUNT(*) > 0 
        FROM ancestors 
        WHERE referrer_id = :currentUserId
        """, nativeQuery = true)
    boolean isUserAncestorOf(@Param("currentUserId") Long currentUserId,
                             @Param("potentialReferrerId") Long potentialReferrerId);

}
