package tg.configshop.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import tg.configshop.model.BotUser;
import tg.configshop.quartz.services.SchedulerService;
import tg.configshop.repositories.BotUserRepository;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationMigration implements CommandLineRunner {

    private final BotUserRepository userRepository;
    private final SchedulerService schedulerService;
    @Value("${MIGRATION_NOTIFICATION}")
    private String flag;

    @Override
    public void run(String... args) {
        String migrate = flag;

        if (!"true".equals(migrate)) {
            return;
        }

        log.info("=== Starting Quartz migration for existing users ===");

        Instant now = Instant.now();
        List<BotUser> users = userRepository.findAll();

        int total = 0;
        int scheduled = 0;
        int expired = 0;
        int errors = 0;

        for (BotUser user : users) {
            total++;

            if (user.getExpireAt() == null) {
                continue;
            }
            if (user.getExpireAt().isBefore(now)) {
                expired++;
                log.debug("User {} subscription expired, skipping", user.getId());
                continue;
            }

            try {
                schedulerService.scheduleSubscriptionNotifications(
                        user.getId(),
                        user.getExpireAt()
                );
                scheduled++;
                log.debug("Scheduled notifications for user {}", user.getId());

            } catch (Exception e) {
                errors++;
                log.error("Failed to schedule for user {}: {}", user.getId(), e.getMessage());
            }
        }

        log.info("=== Migration completed ===");
        log.info("Total users: {}", total);
        log.info("Scheduled: {}", scheduled);
        log.info("Expired subscriptions: {}", expired);
        log.info("Errors: {}", errors);
    }
}
