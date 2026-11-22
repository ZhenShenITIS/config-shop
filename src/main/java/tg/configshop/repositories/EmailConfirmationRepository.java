package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import tg.configshop.model.EmailConfirmation;

import java.util.Optional;
import java.util.UUID;

public interface EmailConfirmationRepository extends JpaRepository<EmailConfirmation, UUID> {
    Optional<EmailConfirmation> findByTokenHash(String tokenHash);
    @Query("select c.webUserId from EmailConfirmation c where c.tokenHash = :tokenHash")
    Optional<UUID> findWebUserIdByTokenHash(String tokenHash);

    Optional<EmailConfirmation> findFirstByWebUserIdOrderByCreatedAtDescIdDesc(UUID webUserId);

    @Modifying
    @Query("delete from EmailConfirmation c where c.webUserId = :webUserId")
    void deleteForUser(UUID webUserId);
}
