package tg.configshop.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tg.configshop.constants.SubscriptionChangeStatus;
import tg.configshop.model.SubscriptionChange;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SubscriptionChangeRepository extends JpaRepository<SubscriptionChange, UUID> {
    boolean existsBySubscriptionIdAndStatusIn(UUID subscriptionId, Collection<SubscriptionChangeStatus> statuses);

    @Query("""
            SELECT change FROM SubscriptionChange change
            WHERE change.status IN :statuses AND change.nextAttemptAt <= :now
            ORDER BY change.nextAttemptAt, change.id
            """)
    List<SubscriptionChange> findDue(Collection<SubscriptionChangeStatus> statuses, Instant now, Pageable page);
}
