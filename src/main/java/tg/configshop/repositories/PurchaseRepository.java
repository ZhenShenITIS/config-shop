package tg.configshop.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tg.configshop.dto.OperationView;
import tg.configshop.model.Purchase;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    @Query(value = """
            WITH operations AS (SELECT value AS amount,
            created_at AS date,
            (CASE top_ups.top_up_source
                WHEN 'REFERRAL' THEN 'Реферальное пополнение'
                WHEN 'EXTERNAL' THEN 'Пополнение баланса'
                WHEN 'PROMO_CODE' THEN 'Применение промокода'
                WHEN 'ADMIN' THEN 'Пополнение администратором'
                ELSE 'Неизвестное пополнение'
            END) AS description,
            'TOP_UP' AS operationType
            FROM shop_db.public.top_ups where bot_user_id = :userId
                        
            UNION ALL 
            
            SELECT paid_amount AS amount,
            created_at AS date,
            (CASE purchases.purchase_type
                WHEN 'DEVICE' THEN CONCAT('Покупка дополнительных устройств (', purchases.device_count, ' шт.)')
                WHEN 'SUBSCRIPTION' THEN CONCAT('Покупка подписки на ', COALESCE(s.duration_days, 0), ' дней и ', COALESCE(s.device_count, 0), ' устройств(а)')
                ELSE 'Неизвестная покупка'
            END) AS description,
            'PURCHASE' AS operationType
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
