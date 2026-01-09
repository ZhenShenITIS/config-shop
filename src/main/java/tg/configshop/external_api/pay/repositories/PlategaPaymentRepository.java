package tg.configshop.external_api.pay.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import tg.configshop.external_api.pay.model.PlategaPayment;

import java.util.Optional;

public interface PlategaPaymentRepository extends JpaRepository<PlategaPayment, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PlategaPayment p WHERE p.transactionId = :id")
    Optional<PlategaPayment> findByIdWithLock(String id);
}
