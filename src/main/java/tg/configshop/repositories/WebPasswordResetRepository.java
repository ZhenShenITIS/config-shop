package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tg.configshop.model.WebPasswordReset;

import java.util.Optional;
import java.util.UUID;

public interface WebPasswordResetRepository extends JpaRepository<WebPasswordReset, UUID> {
    @Query("select r.webUserId from WebPasswordReset r where r.tokenHash = :tokenHash")
    Optional<UUID> findWebUserIdByTokenHash(String tokenHash);
}
