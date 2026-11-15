package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tg.configshop.model.WebRefreshToken;

import java.util.Optional;
import java.util.UUID;

public interface WebRefreshTokenRepository extends JpaRepository<WebRefreshToken, UUID> {
    Optional<WebRefreshToken> findByTokenHash(String tokenHash);
}
