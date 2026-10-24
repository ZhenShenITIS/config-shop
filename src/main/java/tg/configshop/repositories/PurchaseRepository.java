package tg.configshop.repositories;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import tg.configshop.dto.OperationView;
import tg.configshop.model.Purchase;
import java.util.UUID;
public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {
    @Query(value = """
        SELECT l.amount AS amount, l.type AS ledgerType, l.created_at AS date,
          CASE WHEN l.purchase_id IS NOT NULL THEN 'PURCHASE'
               WHEN l.withdrawal_id IS NOT NULL AND l.type = 'WITHDRAWAL' THEN 'WITHDRAW'
               ELSE 'TOP_UP' END AS operationType,
          CASE WHEN l.type = 'WITHDRAWAL_REJECT' THEN 'ADMIN' ELSE t.top_up_source END AS topUpSource,
          p.type AS purchaseType, coalesce(p.sub_devices, p.device_count) AS deviceCount,
          p.sub_days AS durationDays, p.traffic_gb AS trafficGb,
          w.status AS withdrawalStatus,
          w.type AS withdrawalType
        FROM balance_log l
        LEFT JOIN top_ups t ON t.id = l.top_up_id
        LEFT JOIN purchases p ON p.id = l.purchase_id
        LEFT JOIN withdrawals w ON w.id = l.withdrawal_id
        WHERE l.account_id = :accountId ORDER BY l.created_at DESC, l.id DESC
        """, countQuery = "select count(*) from balance_log where account_id = :accountId", nativeQuery = true)
    Page<OperationView> findOperationsByUserId(UUID accountId, Pageable pageable);
}
