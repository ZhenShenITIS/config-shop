package tg.configshop.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tg.configshop.dto.OperationView;
import tg.configshop.model.Purchase;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    // TODO fix this shit
    @Query(value = """
            WITH operations AS (
            SELECT value AS amount,
            created_at AS date,
            'TOP_UP' AS operationType,
            top_up_source AS topUpSource,
            NULL AS purchaseType,
            NULL AS deviceCount,
            NULL AS durationDays
            FROM shop_db.public.top_ups where bot_user_id = :userId
            
            UNION ALL
            
            SELECT paid_amount AS amount,
            created_at AS date,
            'PURCHASE' AS operationType,
            NULL AS topUpSource,
            purchases.purchase_type AS purchaseType,
            (CASE purchase_type
                WHEN 'SUBSCRIPTION' THEN s.device_count
                WHEN 'DEVICE' THEN purchases.device_count
                ELSE 0
            END) AS deviceCount,
            (CASE purchase_type
                WHEN 'SUBSCRIPTION' THEN s.duration_days
                ELSE 0
            END) AS durationDays
            FROM shop_db.public.purchases
            LEFT JOIN public.subscriptions s on s.id = purchases.subscription_id
            where bot_user_id = :userId)
            
            SELECT * FROM operations ORDER BY operations.date DESC;
            """,
            countQuery = """
            SELECT (
                (SELECT COUNT(*) FROM top_ups WHERE bot_user_id = :userId) +
                (SELECT COUNT(*) FROM purchases WHERE bot_user_id = :userId)
            )
            """,
            nativeQuery = true)
    Page<OperationView> findOperationsByUserId (@Param("userId") Long userId, Pageable pageable);
}
