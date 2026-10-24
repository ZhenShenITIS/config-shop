package tg.configshop.repositories;

import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.model.RemnawaveSubscription;
import java.util.*;
public interface RemnawaveSubscriptionRepository extends JpaRepository<RemnawaveSubscription, UUID> {
    List<RemnawaveSubscription> findByAccount_IdOrderByCreatedAtAscIdAsc(UUID accountId);
    Optional<RemnawaveSubscription> findByIdAndAccount_Id(UUID subscriptionId, UUID accountId);
    List<RemnawaveSubscription> findAllByRemnawaveIdIsNullOrderByIdAsc();
    long countByRemnawaveIdIsNull();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RemnawaveSubscription s where s.remnawaveId = :remnawaveId")
    Optional<RemnawaveSubscription> findByRemnawaveIdWithLock(Long remnawaveId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RemnawaveSubscription s where s.remnawaveUuid = :remnawaveUuid")
    Optional<RemnawaveSubscription> findByRemnawaveUuidWithLock(String remnawaveUuid);
    @Modifying @Transactional
    @Query("update RemnawaveSubscription s set s.remnawaveId = :remnawaveId where s.id = :id and s.remnawaveId is null")
    int updateRemnawaveId(UUID id, Long remnawaveId);
}
