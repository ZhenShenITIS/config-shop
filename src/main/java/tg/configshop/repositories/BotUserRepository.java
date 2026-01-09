package tg.configshop.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import tg.configshop.model.BotUser;

import java.util.List;
import java.util.Optional;

public interface BotUserRepository extends JpaRepository<BotUser, Long> {
    List<BotUser> findByUsername(String username);

    List<BotUser> findByUsernameIgnoreCase(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM BotUser u WHERE u.id = :id")
    Optional<BotUser> findByIdWithLock(Long id);
}
