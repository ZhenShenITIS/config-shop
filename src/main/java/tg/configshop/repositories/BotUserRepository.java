package tg.configshop.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.model.BotUser;

import java.util.List;
import java.util.Optional;

public interface BotUserRepository extends JpaRepository<BotUser, Long> {
    List<BotUser> findByUsername(String username);

    List<BotUser> findByUsernameIgnoreCase(String username);

    Optional<BotUser> findByRemnawaveUuid(String remnawaveUuid);

    List<BotUser> findAllByRemnawaveIdIsNullOrderByIdAsc();

    long countByRemnawaveIdIsNull();

    Optional<BotUser> findByRemnawaveId(Long remnawaveId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM BotUser u WHERE u.remnawaveId = :remnawaveId")
    Optional<BotUser> findByRemnawaveIdWithLock(Long remnawaveId);

    @Modifying
    @Transactional
    @Query("UPDATE BotUser u SET u.remnawaveId = :remnawaveId WHERE u.id = :userId")
    int updateRemnawaveId(Long userId, Long remnawaveId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM BotUser u WHERE u.id = :id")
    Optional<BotUser> findByIdWithLock(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM BotUser u WHERE u.remnawaveUuid = :remnawaveUuid")
    Optional<BotUser> findByRemnawaveUuidWithLock(String remnawaveUuid);
}
