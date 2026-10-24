package tg.configshop.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tg.configshop.dto.ReferralView;
import java.util.UUID;
import tg.configshop.model.Referral;

import java.util.Optional;

public interface ReferralRepository extends JpaRepository<Referral, UUID> {
    int countReferralsByReferrer_Id(UUID referrerId);

    Optional<Referral> findByReferral_Id(UUID referralId);

    @Query(value = """
        WITH RECURSIVE ancestors AS (
            SELECT referrer_id
            FROM referrals
            WHERE referral_id = :potentialReferrerId

            UNION

            SELECT r.referrer_id
            FROM referrals r
            INNER JOIN ancestors a ON r.referral_id = a.referrer_id
        )
        SELECT COUNT(*) > 0
        FROM ancestors
        WHERE referrer_id = :accountId
        """, nativeQuery = true)
    boolean isAccountAncestorOf(@Param("accountId") UUID accountId,
                             @Param("potentialReferrerId") UUID potentialReferrerId);




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
            rt.referral_id                AS accountId,
            rt.referred_at                AS referredAt,
            rt.lvl                        AS lvl,
            COALESCE(SUM(t.value), 0)     AS profit
        FROM referral_tree rt
        LEFT JOIN top_ups t
            ON t.referral_id = rt.referral_id AND t.top_up_source = 'REFERRAL' AND t.account_id = :referrerId
        GROUP BY rt.referral_id, rt.referred_at, rt.lvl
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
            @Param("referrerId") UUID referrerId,
            Pageable pageable
    );
}
