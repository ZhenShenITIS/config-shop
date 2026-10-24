package tg.configshop.repositories;

import org.springframework.data.jpa.repository.*;
import tg.configshop.model.BotUser;
import java.util.*;
public interface BotUserRepository extends JpaRepository<BotUser, Long> {
    List<BotUser> findByUsername(String username);
    List<BotUser> findByUsernameIgnoreCase(String username);
    Optional<BotUser> findFirstByAccount_IdOrderByTelegramIdAsc(UUID accountId);

}
