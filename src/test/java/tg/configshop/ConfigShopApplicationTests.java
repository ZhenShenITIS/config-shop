package tg.configshop;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import tg.configshop.model.Account;
import tg.configshop.model.WebUser;
import tg.configshop.model.WebRefreshToken;
import tg.configshop.repositories.WebUserRepository;
import tg.configshop.repositories.WebRefreshTokenRepository;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.repositories.AccountRepository;
import tg.configshop.services.BalanceService;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false", "spring.quartz.auto-startup=false", "payments.polling.enabled=false",
        "spring.jpa.open-in-view=false",
        "WEB_AUTH_JWT_SECRET_BASE64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
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
    @Autowired WebUserRepository webUsers;
    @Autowired WebRefreshTokenRepository refreshTokens;
    @Autowired EntityManager entityManager;
    @Autowired MockMvc mvc;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Test void contextLoadsOnMigratedSchemaWithoutExternalCalls() {
        assertNotNull(balance);
        assertEquals(0, accounts.count());
    }

    @Test
    @Transactional
    void loadsWebProfileAndRefreshTokenFromPostgresAndAuthenticatesJwt() throws Exception {
        UUID accountId = accounts.saveAndFlush(Account.builder().build()).getId();
        Instant verifiedAt = Instant.parse("2026-01-01T00:00:00Z");
        UUID userId = webUsers.saveAndFlush(WebUser.builder().accountId(accountId).email("web@example.com")
                .passwordHash("{bcrypt}test").emailVerifiedAt(verifiedAt).build()).getId();
        UUID familyId = UUID.randomUUID();
        UUID tokenId = refreshTokens.saveAndFlush(WebRefreshToken.builder().webUserId(userId)
                .tokenHash("a".repeat(64)).familyId(familyId).expiresAt(Instant.now().plusSeconds(86400))
                .build()).getId();
        entityManager.clear();
        var user = webUsers.findByEmailIgnoreCase("WEB@EXAMPLE.COM").orElseThrow();
        assertEquals(accountId, user.getAccountId());
        assertEquals(verifiedAt, user.getEmailVerifiedAt());
        var token = refreshTokens.findByTokenHash("a".repeat(64)).orElseThrow();
        assertEquals(tokenId, token.getId());
        assertEquals(userId, token.getWebUserId());
        assertEquals(familyId, token.getFamilyId());
        assertNull(token.getRevokedAt());

        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer("config-shop").audience("config-shop-web-api")
                .subject(userId.toString()).claim("account_id", accountId.toString())
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(900))).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), claims);
        jwt.sign(new MACSigner("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII)));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + jwt.serialize()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webUserId").value(userId.toString()))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.email").value("web@example.com"));
    }

    @Test
    void healthcheckAndMetricsRemainAccessible() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
    }
}
