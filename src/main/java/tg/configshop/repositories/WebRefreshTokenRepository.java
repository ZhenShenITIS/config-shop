package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import tg.configshop.model.WebRefreshToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WebRefreshTokenRepository extends JpaRepository<WebRefreshToken, UUID> {
    Optional<WebRefreshToken> findByTokenHash(String tokenHash);

    @Query("select t.webUserId from WebRefreshToken t where t.tokenHash = :tokenHash")
    Optional<UUID> findWebUserIdByTokenHash(String tokenHash);

    @Modifying
    @Query("update WebRefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    void revokeFamily(UUID familyId, Instant now);

    @Modifying
    @Query("update WebRefreshToken t set t.revokedAt = :now where t.webUserId = :userId and t.revokedAt is null")
    void revokeForUser(UUID userId, Instant now);
}
