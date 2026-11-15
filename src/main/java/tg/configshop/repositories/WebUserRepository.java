package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tg.configshop.model.WebUser;

import java.util.Optional;
import java.util.UUID;

public interface WebUserRepository extends JpaRepository<WebUser, UUID> {
    Optional<WebUser> findByEmailIgnoreCase(String email);
}
