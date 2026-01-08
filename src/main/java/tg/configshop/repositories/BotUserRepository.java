package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tg.configshop.model.BotUser;

import java.util.List;

public interface BotUserRepository extends JpaRepository<BotUser, Long> {
    List<BotUser> findByUsername(String username);

    List<BotUser> findByUsernameIgnoreCase(String username);
}
