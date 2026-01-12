package tg.configshop.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tg.configshop.constants.TopUpSource;
import tg.configshop.dto.ReferralView;
import tg.configshop.dto.ReferralWithProfit;
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




    @Query("""
                SELECT new tg.configshop.dto.ReferralWithProfit(
                    r.referral, 
                    CAST (COALESCE(SUM(tu.value), 0) AS LONG),
                    r.createdAt
                  
                )
                FROM Referral r
                LEFT JOIN TopUp tu 
                    ON tu.referral = r.referral 
                    AND tu.topUpSource = 'REFERRAL'
                WHERE r.referrer.id = :referrerId
                GROUP BY r.referral, r.createdAt
                ORDER BY r.createdAt DESC 
            """)
    Page<ReferralWithProfit> getReferralsWithProfit(
            @Param("referrerId") Long referrerId,
            Pageable pageable
    );

    @Query(value = """
        WITH RECURSIVE referral_tree AS (
        
            SELECT 
                r.referral_id        AS referral_id,
                r.created_at         AS referred_at,
                1                    AS lvl
            FROM referrals r
            WHERE r.referrer_id = :referrerId

            UNION ALL

        
            SELECT 
                r.referral_id        AS referral_id,
                r.created_at         AS referred_at,
                rt.lvl + 1           AS lvl
            FROM referrals r
            JOIN referral_tree rt ON r.referrer_id = rt.referral_id
            WHERE rt.lvl < 3
        )
        SELECT 
            b.id                          AS userId,
            b.first_name                  AS firstName,
            b.expire_at                   AS expireAt,
            rt.referred_at                AS referredAt,
            rt.lvl                        AS lvl,
            COALESCE(SUM(t.value), 0)     AS profit
        FROM referral_tree rt
        JOIN bot_users b ON rt.referral_id = b.id
        LEFT JOIN top_ups t 
            ON t.referral_id = rt.referral_id AND t.top_up_source = 'REFERRAL' AND t.bot_user_id = :referrerId
        GROUP BY b.id, b.first_name, b.expire_at, rt.referred_at, rt.lvl
        ORDER BY rt.lvl, rt.referred_at DESC
        """,
            countQuery = """
        WITH RECURSIVE referral_tree AS (
            
            SELECT 
                r.referral_id, 
                1 AS lvl 
            FROM referrals r
            WHERE r.referrer_id = :referrerId

            UNION ALL

        
            SELECT 
                r.referral_id, 
                rt.lvl + 1 
            FROM referrals r
            JOIN referral_tree rt ON r.referrer_id = rt.referral_id
            WHERE rt.lvl < 3
        )
        SELECT COUNT(*) FROM referral_tree
        """,
            nativeQuery = true)
    Page<ReferralView> findReferralsProjection(
            @Param("referrerId") Long referrerId,
            Pageable pageable
    );
}
