package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tg.configshop.constants.TopUpSource;
import tg.configshop.model.BotUser;
import tg.configshop.model.Referral;

import java.time.Instant;
import java.util.List;

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
}
