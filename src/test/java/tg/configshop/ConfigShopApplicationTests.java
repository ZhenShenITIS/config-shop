package tg.configshop;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.repositories.AccountRepository;
import tg.configshop.services.BalanceService;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false", "spring.quartz.auto-startup=false", "payments.polling.enabled=false",
        "spring.autoconfigure.exclude=org.telegram.telegrambots.longpolling.starter.TelegramBotStarterConfiguration",
        "TELEGRAM_BOT_USERNAME=test", "TELEGRAM_BOT_TOKEN=test", "TELEGRAM_WEBHOOK_URL=http://localhost",
        "CONFIG_PANEL_API_URL=http://localhost", "CONFIG_PANEL_API_KEY=test", "CONFIG_PANEL_SUB_URL=http://localhost",
        "PAY_BASE_URL=http://localhost", "PAY_MERCHANT_ID=test", "PAY_SECRET=test",
        "REMNAWAVE_WEBHOOK_SECRET=test", "REMNAWAVE_WHITELIST_SQUAD_UUID=test", "REMNAWAVE_TRAFFIC_LIMIT_SQUAD_UUID=test",
        "SUPPORT_USERNAME=test", "AGREEMENT_URL=http://localhost", "POLICY_URL=http://localhost", "INSTRUCTION_URL=http://localhost",
        "ALERT_CHAT_ID=1", "ALERT_THREAD_ID=1", "ADMIN_IDS=1", "MIGRATION_NOTIFICATION=false", "FREE_TRAFFIC_GB=1",
        "MIN_DEVICE_COUNT=1", "MAX_DEVICE_COUNT=10", "TRAFFIC_LIMIT_PER_DEVICE=10",
        "BASE_PRICE_FOR_1_MONTH=100", "BASE_PRICE_FOR_3_MONTH=300", "BASE_PRICE_FOR_6_MONTH=600", "BASE_PRICE_FOR_12_MONTH=1200",
        "EXTRA_PER_DEVICE_1_MONTH=10", "EXTRA_PER_DEVICE_3_MONTH=30", "EXTRA_PER_DEVICE_6_MONTH=60", "EXTRA_PER_DEVICE_12_MONTH=120",
        "TRAFFIC_PACKAGE_10_PRICE=10", "TRAFFIC_PACKAGE_30_PRICE=30", "TRAFFIC_PACKAGE_50_PRICE=50",
        "TRAFFIC_PACKAGE_100_PRICE=100", "TRAFFIC_PACKAGE_250_PRICE=250", "TRAFFIC_PACKAGE_500_PRICE=500"
})
class ConfigShopApplicationTests {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @MockitoBean TelegramClient telegramClient;
    @Autowired AccountRepository accounts;
    @Autowired BalanceService balance;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Test void contextLoadsOnMigratedSchemaWithoutExternalCalls() {
        assertNotNull(balance);
        assertEquals(0, accounts.count());
    }
}
