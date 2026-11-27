package tg.configshop.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import tg.configshop.constants.SubscriptionChangeStatus;
import tg.configshop.model.SubscriptionChange;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionChangeRepository extends JpaRepository<SubscriptionChange, UUID> {
    boolean existsBySubscriptionIdAndStatusIn(UUID subscriptionId, Collection<SubscriptionChangeStatus> statuses);

    @Modifying
    @Query(value = """
            INSERT INTO subscription_changes (id, subscription_id, days, devices, price, expected_expiry, status, next_attempt_at)
            VALUES (:id, :subscriptionId, :days, :devices, :price, :expectedExpiry, 'PREPARING', :now)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    void reserveRenewal(UUID id, UUID subscriptionId, int days, int devices, long price, Instant expectedExpiry, Instant now);

    @Query("""
            SELECT change FROM SubscriptionChange change, RemnawaveSubscription subscription
            WHERE change.id = :id AND change.days > 0
              AND change.subscriptionId = :subscriptionId AND subscription.id = change.subscriptionId
              AND subscription.account.id = :accountId
            """)
    Optional<SubscriptionChange> findOwnedRenewal(UUID id, UUID subscriptionId, UUID accountId);

    @Query("""
            SELECT change FROM SubscriptionChange change
            WHERE change.status IN :statuses AND change.nextAttemptAt <= :now
            ORDER BY change.nextAttemptAt, change.id
            """)
    List<SubscriptionChange> findDue(Collection<SubscriptionChangeStatus> statuses, Instant now, Pageable page);
}
