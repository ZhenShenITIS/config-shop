package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import tg.configshop.model.WebUser;

import java.util.Optional;
import java.util.UUID;

public interface WebUserRepository extends JpaRepository<WebUser, UUID> {
    Optional<WebUser> findByEmailIgnoreCase(String email);

    boolean existsByAccountId(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from WebUser u where u.id = :id")
    Optional<WebUser> findByIdWithLock(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from WebUser u where lower(u.email) = lower(:email)")
    Optional<WebUser> findByEmailWithLock(String email);
}
