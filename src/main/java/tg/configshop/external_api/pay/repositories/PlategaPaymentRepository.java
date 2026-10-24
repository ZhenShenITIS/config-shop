package tg.configshop.external_api.pay.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import tg.configshop.external_api.pay.model.PlategaPayment;
import tg.configshop.external_api.pay.constants.PaymentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlategaPaymentRepository extends JpaRepository<PlategaPayment, java.util.UUID> {
    String SNAPSHOT = """
            SELECT p.id AS id, p.transactionId AS providerPaymentId, p.account.id AS accountId,
                   p.createdAt AS createdAt, p.status AS status FROM PlategaPayment p
            """;
    String PENDING = SNAPSHOT + """
            WHERE p.status = tg.configshop.external_api.pay.constants.PaymentStatus.PENDING
              AND p.createdAt <= :createdBefore
              AND NOT EXISTS (SELECT t.id FROM TopUp t WHERE t.platega.id = p.id)
            """;

    @Query(SNAPSHOT + " WHERE p.transactionId = :id")
    Optional<PaymentSnapshot> findForCheck(String id);

    @Query(PENDING + " ORDER BY p.createdAt, p.id")
    List<PaymentSnapshot> findPending(Instant createdBefore, Pageable pageable);

    @Query(PENDING + """
            AND (p.createdAt > :afterCreatedAt OR (p.createdAt = :afterCreatedAt AND p.id > :afterId))
            ORDER BY p.createdAt, p.id
            """)
    List<PaymentSnapshot> findPendingAfter(Instant createdBefore, Instant afterCreatedAt, UUID afterId, Pageable pageable);

    interface PaymentSnapshot {
        UUID getId();
        String getProviderPaymentId();
        UUID getAccountId();
        Instant getCreatedAt();
        PaymentStatus getStatus();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PlategaPayment p WHERE p.transactionId = :id")
    Optional<PlategaPayment> findByIdWithLock(String id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PlategaPayment p WHERE p.id = :id")
    Optional<PlategaPayment> findInternalByIdWithLock(java.util.UUID id);
}
