package tg.configshop.repositories;

import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import tg.configshop.model.Account;
import java.util.*;
public interface AccountRepository extends JpaRepository<Account, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdWithLock(UUID id);
}
