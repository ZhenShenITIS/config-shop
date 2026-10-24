package tg.configshop.repositories;

import tg.configshop.constants.BalanceLogType;
import org.springframework.data.jpa.repository.*;
import tg.configshop.model.BalanceLog;
import java.util.UUID;
public interface BalanceLogRepository extends JpaRepository<BalanceLog, UUID> {
    @Query("select coalesce(sum(l.amount), 0) from BalanceLog l where l.account.id = :accountId")
    long balance(UUID accountId);
    boolean existsByPurchase_Id(UUID id);
    boolean existsByTopUp_Id(UUID id);
    boolean existsByWithdrawal_IdAndType(UUID id, BalanceLogType type);
}
