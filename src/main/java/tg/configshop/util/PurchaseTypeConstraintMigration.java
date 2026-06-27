package tg.configshop.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PurchaseTypeConstraintMigration implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        jdbcTemplate.execute("""
                ALTER TABLE purchases
                DROP CONSTRAINT IF EXISTS purchases_purchase_type_check
                """);

        jdbcTemplate.execute("""
                ALTER TABLE purchases
                ADD CONSTRAINT purchases_purchase_type_check
                CHECK (purchase_type IN ('DEVICE', 'SUBSCRIPTION', 'TRAFFIC'))
                """);

        log.info("[Migration] purchases_purchase_type_check updated");
    }
}
