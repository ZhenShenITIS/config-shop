package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tg.configshop.model.BotUser;

public interface BotUserRepository extends JpaRepository<BotUser, Long> {
}
