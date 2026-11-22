package tg.configshop.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.model.Account;
import tg.configshop.model.EmailConfirmation;
import tg.configshop.model.PromoCode;
import tg.configshop.model.RemnawaveSubscription;
import tg.configshop.model.WebUser;
import tg.configshop.quartz.services.SchedulerService;
import tg.configshop.repositories.*;
import tg.configshop.services.BalanceService;
import tg.configshop.services.ReferralService;
import tg.configshop.web.registration.RegistrationRateLimiter;
import tg.configshop.web.registration.TelegramWebLinkService;
import tg.configshop.web.registration.WebAuthException;
import tg.configshop.telegram.TelegramUserService;
import tg.configshop.constants.CallbackName;
import tg.configshop.telegram.handlers.CallbackQueryHandler;
import tg.configshop.telegram.handlers.MessageHandler;
import tg.configshop.telegram.message_body.StartCommandBody;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource("classpath:web-registration.properties")
@Testcontainers(disabledWithoutDocker = true)
class WebRegistrationIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    private static final String PASSWORD = "my-test-password";
    private static final String REFRESH_COOKIE = "__Host-config_shop_refresh";
    private static final String CSRF_HEADER = "X-CSRF-Protection";
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @MockitoBean TelegramClient telegram;
    @MockitoBean RemnawaveClient remote;
    @MockitoBean JavaMailSender sender;
    @MockitoBean SchedulerService scheduler;
    @MockitoBean RegistrationRateLimiter limits;
    @MockitoBean Clock clock;
    @MockitoSpyBean EmailConfirmationRepository confirmations;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebUserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired PromoCodeRepository promoCodes;
    @Autowired ReferralRepository referralRepository;
    @Autowired RemnawaveSubscriptionRepository subscriptions;
    @Autowired ReferralService referrals;
    @Autowired BalanceService balances;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired WebRefreshTokenRepository refreshTokens;
    @Autowired WebPasswordResetRepository resets;
    @Autowired TelegramUserService telegramUsers;
    @Autowired CallbackQueryHandler callbackHandler;
    @Autowired MessageHandler messageHandler;
    @Autowired StartCommandBody startMenu;
    @Autowired TelegramWebLinkService telegramLinks;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE TABLE accounts CASCADE");
        when(clock.instant()).thenReturn(NOW);
        when(remote.createBasicUser(anyString())).thenReturn(RemnawaveUserResponse.builder()
                .id(1234L).uuid("panel-uuid").shortUuid("short-web").expireAt(NOW.plusSeconds(432000)).build());
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "SMTP must run after commit");
            return null;
        }).when(sender).send(any(SimpleMailMessage.class));
    }

    @Test
    void registrationNormalizesEmailAndStoresOnlyPasswordAndTokenHashes() throws Exception {
        register("  USER@Example.com  ", PASSWORD, null);
        var user = users.findByEmailIgnoreCase("user@example.com").orElseThrow();
        assertEquals("user@example.com", user.getEmail());
        assertEquals(1, accounts.count());
        assertTrue(accounts.existsById(user.getAccountId()));
        assertTrue(passwordEncoder.matches(PASSWORD, user.getPasswordHash()));
        assertNotEquals(PASSWORD, user.getPasswordHash());
        assertNull(user.getEmailVerifiedAt());
        var confirmation = confirmations.findFirstByWebUserIdOrderByCreatedAtDescIdDesc(user.getId()).orElseThrow();
        assertEquals(NOW.plusSeconds(86400), confirmation.getExpiresAt());
        assertTrue(confirmation.getTokenHash().matches("[a-f0-9]{64}"));
        assertFalse(confirmation.getTokenHash().contains(lastToken()));
        assertEquals(0, balances.balance(user.getAccountId()));
        assertEquals(0, promoCodes.count());
        verifyNoInteractions(remote, scheduler);
        mvc.perform(get("/api/auth/confirm-email").param("token", lastToken())).andExpect(status().isUnauthorized());
        assertNull(users.findById(user.getId()).orElseThrow().getEmailVerifiedAt());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void confirmsOnceCreatesTrialAndReferralWithoutCreditingBalance() throws Exception {
        UUID referrer = accounts.saveAndFlush(Account.builder().build()).getId();
        referrals.createReferralCode(referrer);
        String code = referrals.getReferralPromoCodes(referrer).getFirst();
        register("user@example.com", PASSWORD, code.toLowerCase());
        assertEquals(0, referralRepository.count());
        String token = lastToken();
        var user = users.findByEmailIgnoreCase("user@example.com").orElseThrow();
        confirm(token).andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.trialCreated").value(true))
                .andExpect(jsonPath("$.accountId").value(user.getAccountId().toString()));
        confirm(token).andExpect(status().isOk()).andExpect(jsonPath("$.trialCreated").value(true));
        assertEquals(NOW, users.findById(user.getId()).orElseThrow().getEmailVerifiedAt());
        assertEquals(NOW, confirmations.findFirstByWebUserIdOrderByCreatedAtDescIdDesc(user.getId()).orElseThrow().getUsedAt());
        assertEquals(referrer, referrals.getReferrerId(user.getAccountId()).orElseThrow());
        assertEquals(1, referralRepository.count());
        assertEquals(1, referrals.getReferralPromoCodes(user.getAccountId()).size());
        assertEquals(1, subscriptions.count());
        assertEquals(0, balances.balance(user.getAccountId()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM promo_code_uses", Integer.class));
        verify(remote, times(1)).createBasicUser("a_" + user.getAccountId().toString().replace("-", ""));
        verify(scheduler, times(1)).scheduleTrialTrafficChecks(eq(user.getAccountId()), any());
    }

    @Test
    void panelFailurePreservesConfirmationAndDoesNotRetry() throws Exception {
        register("user@example.com", PASSWORD, null);
        String token = lastToken();
        when(remote.createBasicUser(anyString())).thenThrow(new IllegalStateException("panel unavailable"));
        confirm(token).andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.trialCreated").value(false))
                .andExpect(jsonPath("$.message").value(containsString("поддержку")));
        var user = users.findByEmailIgnoreCase("user@example.com").orElseThrow();
        assertNotNull(user.getEmailVerifiedAt());
        assertEquals(1, accounts.count());
        assertEquals(0, subscriptions.count());
        assertEquals(1, referrals.getReferralPromoCodes(user.getAccountId()).size());
        confirm(token).andExpect(status().isOk()).andExpect(jsonPath("$.trialCreated").value(false));
        verify(remote, times(1)).createBasicUser(anyString());
        verifyNoInteractions(scheduler);
    }

    @Test
    void resendReplacesTokenPreservesReferrerAndEnforcesCooldown() throws Exception {
        UUID referrer = accounts.saveAndFlush(Account.builder().build()).getId();
        referrals.createReferralCode(referrer);
        register("user@example.com", PASSWORD, referrals.getReferralPromoCodes(referrer).getFirst());
        String oldToken = lastToken();
        resend("USER@example.com").andExpect(status().isTooManyRequests());
        when(clock.instant()).thenReturn(NOW.plusSeconds(61));
        resend("  USER@EXAMPLE.com  ").andExpect(status().isAccepted());
        String newToken = lastToken();
        assertNotEquals(oldToken, newToken);
        assertEquals(1, confirmations.count());
        confirm(oldToken).andExpect(status().isBadRequest());
        confirm(newToken).andExpect(status().isOk()).andExpect(jsonPath("$.trialCreated").value(true));
        UUID accountId = users.findByEmailIgnoreCase("user@example.com").orElseThrow().getAccountId();
        assertEquals(referrer, referrals.getReferrerId(accountId).orElseThrow());
    }

    @Test
    void smtpFailureKeepsAccountAndAllowsResendingWithoutChangingPassword() throws Exception {
        doThrow(new MailSendException("smtp down")).when(sender).send(any(SimpleMailMessage.class));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(payload("user@example.com", PASSWORD, null)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("confirmation_delivery_failed"));
        var user = users.findByEmailIgnoreCase("user@example.com").orElseThrow();
        assertEquals(1, accounts.count());
        assertNull(user.getEmailVerifiedAt());
        assertTrue(passwordEncoder.matches(PASSWORD, user.getPasswordHash()));
        doNothing().when(sender).send(any(SimpleMailMessage.class));
        when(clock.instant()).thenReturn(NOW.plusSeconds(61));
        resend(user.getEmail()).andExpect(status().isAccepted());
        confirm(lastToken()).andExpect(status().isOk()).andExpect(jsonPath("$.trialCreated").value(true));
    }

    @Test
    void duplicateEmailDoesNotCreateAccountOrReplacePassword() throws Exception {
        register("user@example.com", PASSWORD, null);
        String hash = users.findByEmailIgnoreCase("user@example.com").orElseThrow().getPasswordHash();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(payload("USER@EXAMPLE.COM", "another-password", null)))
                .andExpect(status().isConflict());
        assertEquals(1, accounts.count());
        assertEquals(1, users.count());
        assertEquals(1, confirmations.count());
        assertEquals(hash, users.findByEmailIgnoreCase("user@example.com").orElseThrow().getPasswordHash());
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void persistenceFailureRollsBackAccountProfileAndConfirmation() {
        doThrow(new DataIntegrityViolationException("forced failure"))
                .when(confirmations).saveAndFlush(any(EmailConfirmation.class));
        assertThrows(ServletException.class, () -> register("user@example.com", PASSWORD, null));
        assertEquals(0, accounts.count());
        assertEquals(0, users.count());
        assertEquals(0, confirmations.count());
        verifyNoInteractions(sender, remote);
    }

    @Test
    void invalidReferralAndOrdinaryPromoAreRejectedBeforeCreatingAccount() throws Exception {
        promoCodes.saveAndFlush(PromoCode.builder().code("ORDINARY").amount(100L).build());
        for (String code : List.of("UNKNOWN", "ORDINARY")) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(payload("user@example.com", PASSWORD, code)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_referral_code"));
        }
        assertEquals(0, accounts.count());
        assertEquals(0, users.count());
        verifyNoInteractions(sender, remote);
    }

    @Test
    void expiredAndUnknownTokensDoNotConfirmOrIssueTrial() throws Exception {
        register("user@example.com", PASSWORD, null);
        String token = lastToken();
        confirm("A".repeat(43)).andExpect(status().isBadRequest());
        when(clock.instant()).thenReturn(NOW.plusSeconds(86400));
        confirm(token).andExpect(status().isBadRequest());
        assertNull(users.findByEmailIgnoreCase("user@example.com").orElseThrow().getEmailVerifiedAt());
        verifyNoInteractions(remote);
    }

    @Test
    void resendForMissingAndConfirmedAddressesHasSameResponseAndSendsNothing() throws Exception {
        register("user@example.com", PASSWORD, null);
        confirm(lastToken()).andExpect(status().isOk());
        clearInvocations(sender);
        String missing = resend("missing@example.com").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String confirmed = resend("user@example.com").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        assertEquals(missing, confirmed);
        verifyNoInteractions(sender);
    }

    @Test
    void concurrentRegistrationsLeaveExactlyOneAccount() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var attempts = Stream.generate(() -> pool.submit(() -> {
                start.await();
                return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(payload("user@example.com", PASSWORD, null))).andReturn().getResponse().getStatus();
            })).limit(2).toList();
            start.countDown();
            var statuses = List.of(attempts.getFirst().get(15, TimeUnit.SECONDS), attempts.getLast().get(15, TimeUnit.SECONDS));
            assertTrue(statuses.containsAll(List.of(201, 409)));
        }
        assertEquals(1, accounts.count());
        assertEquals(1, users.count());
        assertEquals(1, confirmations.count());
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void concurrentConfirmationsMakeOnePanelRequest() throws Exception {
        register("user@example.com", PASSWORD, null);
        String token = lastToken();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var attempts = Stream.generate(() -> pool.submit(() -> {
                start.await();
                return confirm(token).andReturn().getResponse().getStatus();
            })).limit(2).toList();
            start.countDown();
            for (var attempt : attempts) assertEquals(200, attempt.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, subscriptions.count());
        verify(remote, times(1)).createBasicUser(anyString());
    }

    @ParameterizedTest
    @MethodSource("invalidRegistrations")
    void validatesRegistrationWithoutLeakingPassword(String body) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_request"));
        assertEquals(0, accounts.count());
        verifyNoInteractions(sender, remote);
    }

    static Stream<String> invalidRegistrations() throws Exception {
        return Stream.of("{}", "{broken-json", payload("not-an-email", PASSWORD, null),
                payload("user@example.com", "short", null), payload("user@example.com", "a".repeat(73), null),
                payload("user@example.com", "я".repeat(37), null), payload("user@example.com", " ".repeat(12), null));
    }

    @Test
    void returnsJsonForRateLimits() throws Exception {
        doThrow(new tg.configshop.web.registration.WebAuthException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "rate_limit_exceeded", "Попробуйте позже."))
                .when(limits).register(anyString());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(payload("user@example.com", PASSWORD, null)))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error").value("rate_limit_exceeded"));
        assertEquals(0, accounts.count());
    }

    @Test
    void loginIssuesJwtAndProtectsCookieRotationAndLogoutFromCsrf() throws Exception {
        register("user@example.com", PASSWORD, null);
        login("user@example.com", PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
        confirm(lastToken()).andExpect(status().isOk());
        UUID accountId = users.findByEmailIgnoreCase("user@example.com").orElseThrow().getAccountId();
        when(clock.instant()).thenReturn(Instant.now());

        var login = login(" USER@example.com ", PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
                .andExpect(cookie().secure(REFRESH_COOKIE, true))
                .andExpect(cookie().path(REFRESH_COOKIE, "/")).andReturn().getResponse();
        String accessToken = new ObjectMapper().readTree(login.getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(accountId.toString()));
        Cookie first = login.getCookie(REFRESH_COOKIE);
        assertEquals(WebTokens.hash(first.getValue()), refreshTokens.findAll().getFirst().getTokenHash());
        mvc.perform(post("/api/auth/refresh").cookie(first)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(first)).andExpect(status().isForbidden());
        mvc.perform(options("/api/auth/refresh").header(HttpHeaders.ORIGIN, "https://another.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, CSRF_HEADER))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        Cookie second = mvc.perform(post("/api/auth/refresh").header(CSRF_HEADER, "1").cookie(first)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(REFRESH_COOKIE);
        assertNotEquals(first.getValue(), second.getValue());
        var original = refreshTokens.findByTokenHash(WebTokens.hash(first.getValue())).orElseThrow();
        var replacement = refreshTokens.findByTokenHash(WebTokens.hash(second.getValue())).orElseThrow();
        assertEquals(original.getFamilyId(), replacement.getFamilyId());
        assertEquals(original.getExpiresAt(), replacement.getExpiresAt());
        assertEquals(replacement.getId(), original.getReplacedById());
        mvc.perform(post("/api/auth/logout").header(CSRF_HEADER, "1").cookie(second)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired"))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge(REFRESH_COOKIE, 0));
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRefreshMakesOneReplacementAndRevokesItsFamilyOnReplay() throws Exception {
        register("user@example.com", PASSWORD, null);
        confirm(lastToken()).andExpect(status().isOk());
        Cookie first = loginCookie("user@example.com", PASSWORD);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var attempts = Stream.generate(() -> pool.submit(() -> {
                start.await();
                return refresh(first).andReturn().getResponse();
            })).limit(2).toList();
            start.countDown();
            var a = attempts.get(0).get(15, TimeUnit.SECONDS);
            var b = attempts.get(1).get(15, TimeUnit.SECONDS);
            assertEquals(List.of(200, 401), Stream.of(a.getStatus(), b.getStatus()).sorted().toList());
            Cookie replacement = (a.getStatus() == 200 ? a : b).getCookie(REFRESH_COOKIE);
            assertTrue(refreshTokens.findAll().stream().allMatch(token -> token.getRevokedAt() != null));
            assertEquals(2, refreshTokens.count());
            refresh(replacement).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void passwordResetIsOneTimeAndRevokesRefreshTokensAcrossDevices() throws Exception {
        register("user@example.com", PASSWORD, null);
        confirm(lastToken()).andExpect(status().isOk());
        Cookie first = loginCookie("user@example.com", PASSWORD);
        Cookie second = loginCookie("user@example.com", PASSWORD);
        String unknown = mvc.perform(postJson("/api/auth/forgot-password", Map.of("email", "unknown@example.com")))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String known = mvc.perform(postJson("/api/auth/forgot-password", Map.of("email", "user@example.com")))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertEquals(unknown, known);
        verify(sender, timeout(5000).times(2)).send(any(SimpleMailMessage.class));
        String oldToken = lastMailToken("reset-password");
        when(clock.instant()).thenReturn(NOW.plusSeconds(61));
        mvc.perform(postJson("/api/auth/forgot-password", Map.of("email", "user@example.com")))
                .andExpect(status().isAccepted());
        verify(sender, timeout(5000).times(3)).send(any(SimpleMailMessage.class));
        String newToken = lastMailToken("reset-password");
        assertEquals(WebTokens.hash(newToken), resets.findAll().getFirst().getTokenHash());

        String password = "new-test-password";
        mvc.perform(postJson("/api/auth/reset-password", Map.of("token", oldToken, "password", password)))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/auth/reset-password", Map.of("token", newToken, "password", password)))
                .andExpect(status().isNoContent());
        mvc.perform(postJson("/api/auth/reset-password", Map.of("token", newToken, "password", password)))
                .andExpect(status().isBadRequest());
        refresh(first).andExpect(status().isUnauthorized());
        refresh(second).andExpect(status().isUnauthorized());
        login("user@example.com", PASSWORD).andExpect(status().isUnauthorized());
        login("user@example.com", password).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void codeMergesIntoOlderAccountAndMovesDependentRecords(boolean telegramIsOlder) throws Exception {
        when(clock.instant()).thenReturn(Instant.now());
        var older = accounts.saveAndFlush(Account.builder().createdAt(NOW.minusSeconds(60)).build());
        var newer = accounts.saveAndFlush(Account.builder().createdAt(NOW).build());
        UUID keep = older.getId();
        UUID move = newer.getId();
        var telegramAccount = telegramIsOlder ? older : newer;
        var webAccount = telegramIsOlder ? newer : older;
        jdbc.update("INSERT INTO bot_users(telegram_id, account_id) VALUES (?, ?)", 42L, telegramAccount.getId());
        var webUser = verifiedWebUser(webAccount.getId(), "user@example.com");
        UUID telegramSubscription = subscriptions.saveAndFlush(RemnawaveSubscription.builder().account(telegramAccount)
                .remnawaveId(42L).build()).getId();
        UUID webSubscription = subscriptions.saveAndFlush(RemnawaveSubscription.builder().account(webAccount)
                .remnawaveId(43L).build()).getId();
        UUID movedSubscription = telegramIsOlder ? webSubscription : telegramSubscription;

        balances.adjustAsAdmin(keep, 100);
        UUID payment = jdbc.queryForObject("""
                INSERT INTO platega_payments(provider_payment_id, account_id, amount, currency, status, payment_method)
                VALUES ('payment-merge', ?, 200, 'RUB', 'CONFIRMED', 'SBPQR') RETURNING id
                """, UUID.class, move);
        UUID topUp = jdbc.queryForObject("""
                INSERT INTO top_ups (account_id, value, top_up_source, platega_id)
                VALUES (?, 200, 'EXTERNAL', ?) RETURNING id
                """, UUID.class, move, payment);
        UUID referralTopUp = jdbc.queryForObject("""
                INSERT INTO top_ups (account_id, value, top_up_source, referral_id, source_payment_id, referral_level)
                VALUES (?, 30, 'REFERRAL', ?, ?, 1) RETURNING id
                """, UUID.class, keep, move, payment);
        UUID movedReferralTopUp = jdbc.queryForObject("""
                INSERT INTO top_ups (account_id, value, top_up_source, referral_id, source_payment_id, referral_level)
                VALUES (?, 25, 'REFERRAL', ?, ?, 1) RETURNING id
                """, UUID.class, move, move, payment);
        UUID purchase = jdbc.queryForObject("""
                INSERT INTO purchases (account_id, remnawave_subscription_id, paid_amount, type, traffic_gb)
                VALUES (?, ?, 50, 'TRAFFIC', 10) RETURNING id
                """, UUID.class, move, movedSubscription);
        UUID withdrawal = jdbc.queryForObject("""
                INSERT INTO withdrawals (account_id, amount, status, type, requisites)
                VALUES (?, 40, 'IN_PROGRESS', 'CARD', 'test') RETURNING id
                """, UUID.class, move);
        jdbc.update("INSERT INTO balance_log (account_id, amount, type, top_up_id) VALUES (?, 200, 'TOP_UP_EXTERNAL', ?)", move, topUp);
        jdbc.update("INSERT INTO balance_log (account_id, amount, type, top_up_id) VALUES (?, 30, 'TOP_UP_REFERRAL', ?)", keep, referralTopUp);
        jdbc.update("INSERT INTO balance_log (account_id, amount, type, top_up_id) VALUES (?, 25, 'TOP_UP_REFERRAL', ?)", move, movedReferralTopUp);
        jdbc.update("INSERT INTO balance_log (account_id, amount, type, purchase_id) VALUES (?, -50, 'TRAFFIC_PURCHASE', ?)", move, purchase);
        jdbc.update("INSERT INTO balance_log (account_id, amount, type, withdrawal_id) VALUES (?, -40, 'WITHDRAWAL', ?)", move, withdrawal);

        UUID parent = accounts.saveAndFlush(Account.builder().build()).getId();
        UUID otherParent = accounts.saveAndFlush(Account.builder().build()).getId();
        UUID child = accounts.saveAndFlush(Account.builder().build()).getId();
        jdbc.update("INSERT INTO referrals (referrer_id, referral_id) VALUES (?, ?), (?, ?), (?, ?)",
                parent, keep, otherParent, move, move, child);
        UUID promo = jdbc.queryForObject("""
                INSERT INTO promo_codes (code, amount, current_uses, is_referral, referrer_id)
                VALUES ('moved-code', 1, 2, true, ?) RETURNING id
                """, UUID.class, move);
        jdbc.update("INSERT INTO promo_code_uses (promo_code_id, account_id) VALUES (?, ?), (?, ?)", promo, keep, promo, move);
        UUID pendingAccount = accounts.saveAndFlush(Account.builder().build()).getId();
        UUID pendingUser = users.saveAndFlush(WebUser.builder().accountId(pendingAccount)
                .email("pending@example.com").passwordHash(webUser.getPasswordHash()).build()).getId();
        jdbc.update("""
                INSERT INTO email_confirmations (web_user_id, token_hash, referrer_account_id, expires_at)
                VALUES (?, ?, ?, now() + interval '1 day')
                """, pendingUser, "e".repeat(64), move);

        mvc.perform(post("/api/auth/link-telegram")).andExpect(status().isUnauthorized());
        mvc.perform(get("/link-telegram.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"generate\"")));
        var session = login("user@example.com", PASSWORD).andExpect(status().isOk()).andReturn().getResponse();
        String accessToken = new ObjectMapper().readTree(session.getContentAsString()).get("accessToken").asText();
        String code = codeFrom(accessToken);
        assertTrue(code.matches("[0-9]{6}"));
        assertNotEquals(code, jdbc.queryForObject("SELECT code_hash FROM web_telegram_links", String.class));

        enterCodeInBot(code);
        assertThrows(WebAuthException.class, () -> telegramLinks.redeem(42L, code));
        assertFalse(accounts.existsById(move));
        assertEquals(keep, users.findById(webUser.getId()).orElseThrow().getAccountId());
        assertEquals(keep, telegramUsers.accountId(42L));
        assertEquals(telegramSubscription, telegramUsers.subscriptionId(42L));
        assertEquals(2, subscriptions.findByAccount_IdOrderByCreatedAtAscIdAsc(keep).size());
        assertEquals(265, balances.balance(keep));
        assertEquals(keep, jdbc.queryForObject("SELECT account_id FROM platega_payments WHERE id = ?", UUID.class, payment));
        assertEquals(keep, jdbc.queryForObject("SELECT referral_id FROM top_ups WHERE id = ?", UUID.class, referralTopUp));
        assertEquals(move, jdbc.queryForObject("SELECT original_reward_account_id FROM top_ups WHERE id = ?", UUID.class, movedReferralTopUp));
        assertEquals(keep, jdbc.queryForObject("SELECT account_id FROM withdrawals WHERE id = ?", UUID.class, withdrawal));
        assertEquals(parent, jdbc.queryForObject("SELECT referrer_id FROM referrals WHERE referral_id = ?", UUID.class, keep));
        assertEquals(keep, jdbc.queryForObject("SELECT referrer_id FROM referrals WHERE referral_id = ?", UUID.class, child));
        assertEquals(keep, jdbc.queryForObject("SELECT referrer_id FROM promo_codes WHERE id = ?", UUID.class, promo));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM promo_code_uses WHERE promo_code_id = ?", Integer.class, promo));
        assertEquals(keep, jdbc.queryForObject("SELECT referrer_account_id FROM email_confirmations WHERE web_user_id = ?", UUID.class, pendingUser));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM web_telegram_links", Integer.class));
        var refreshed = refresh(session.getCookie(REFRESH_COOKIE)).andExpect(status().isOk()).andReturn().getResponse();
        String newToken = new ObjectMapper().readTree(refreshed.getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(keep.toString()));
        mvc.perform(post("/api/auth/link-telegram").header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("telegram_already_linked"))
                .andExpect(jsonPath("$.message").value("Telegram уже подключён к этому аккаунту."));
        assertEquals("telegram_already_linked", assertThrows(WebAuthException.class,
                () -> telegramLinks.issue(webUser.getId())).getError());
        verifyNoInteractions(remote, scheduler, sender);
    }

    @Test
    void codeExpiresAndOnlyOneTelegramProfileCanRedeemAReplacement() throws Exception {
        UUID webAccount = accounts.saveAndFlush(Account.builder().build()).getId();
        var webUser = verifiedWebUser(webAccount, "user@example.com");
        UUID firstBot = accounts.saveAndFlush(Account.builder().build()).getId();
        UUID secondBot = accounts.saveAndFlush(Account.builder().build()).getId();
        jdbc.update("INSERT INTO bot_users(telegram_id, account_id) VALUES (?, ?), (?, ?)", 42L, firstBot, 43L, secondBot);
        String first = telegramLinks.issue(webUser.getId()).code();
        String second = telegramLinks.issue(webUser.getId()).code();
        assertNotEquals(first, second);
        assertThrows(WebAuthException.class, () -> telegramLinks.redeem(42L, first));
        when(clock.instant()).thenReturn(NOW.plusSeconds(300));
        assertThrows(WebAuthException.class, () -> telegramLinks.redeem(42L, second));
        String current = telegramLinks.issue(webUser.getId()).code();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var attempts = Stream.of(42L, 43L).map(id -> pool.submit(() -> {
                start.await();
                try {
                    telegramLinks.redeem(id, current);
                    return true;
                } catch (WebAuthException e) {
                    assertEquals("invalid_telegram_code", e.getError());
                    return false;
                }
            })).toList();
            start.countDown();
            assertNotEquals(attempts.get(0).get(15, TimeUnit.SECONDS), attempts.get(1).get(15, TimeUnit.SECONDS));
        }
        assertEquals(2, accounts.count());
    }

    @Test
    void mergingAccountsDropsSelfReferralAndKeepsDescendants() {
        UUID keep = accounts.saveAndFlush(Account.builder().createdAt(NOW.minusSeconds(1)).build()).getId();
        UUID move = accounts.saveAndFlush(Account.builder().createdAt(NOW).build()).getId();
        UUID descendant = accounts.saveAndFlush(Account.builder().build()).getId();
        var web = verifiedWebUser(move, "user@example.com");
        jdbc.update("INSERT INTO bot_users(telegram_id, account_id) VALUES (?, ?)", 42L, keep);
        jdbc.update("INSERT INTO referrals (referrer_id, referral_id) VALUES (?, ?), (?, ?)", keep, move, move, descendant);
        assertEquals(keep, telegramLinks.redeem(42L, telegramLinks.issue(web.getId()).code()));
        assertEquals(1, referralRepository.count());
        assertEquals(keep, jdbc.queryForObject("SELECT referrer_id FROM referrals WHERE referral_id = ?", UUID.class, descendant));
    }

    @Test
    void mainMenuHidesWebsiteButtonAfterLinking() {
        UUID telegramAccount = accounts.saveAndFlush(Account.builder().build()).getId();
        UUID webAccount = accounts.saveAndFlush(Account.builder().build()).getId();
        jdbc.update("INSERT INTO bot_users(telegram_id, account_id) VALUES (?, ?)", 42L, telegramAccount);
        var web = verifiedWebUser(webAccount, "user@example.com");
        var user = new User(42L, "Test", false);

        assertTrue(hasConnectWebsiteButton(user));
        telegramLinks.redeem(42L, telegramLinks.issue(web.getId()).code());
        assertFalse(hasConnectWebsiteButton(user));
    }

    private boolean hasConnectWebsiteButton(User user) {
        return startMenu.getMessage(user, null).inlineKeyboard().getKeyboard().stream()
                .flatMap(Collection::stream)
                .anyMatch(button -> CallbackName.CONNECT_WEBSITE.getCallbackName().equals(button.getCallbackData()));
    }

    private WebUser verifiedWebUser(UUID accountId, String email) {
        return users.saveAndFlush(WebUser.builder().accountId(accountId).email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD)).emailVerifiedAt(NOW).build());
    }

    private void enterCodeInBot(String code) throws Exception {
        var from = new User(42L, "Test", false);
        var message = mock(Message.class);
        when(message.getChatId()).thenReturn(42L);
        when(message.getFrom()).thenReturn(from);
        when(message.getText()).thenReturn(code);
        when(message.hasText()).thenReturn(true);
        var query = new CallbackQuery();
        query.setId("connect-website");
        query.setFrom(from);
        query.setMessage(message);
        query.setData(CallbackName.CONNECT_WEBSITE.getCallbackName());
        callbackHandler.processCallbackQuery(query, telegram);
        messageHandler.answerMessage(message, telegram);
        var sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegram, times(2)).execute(sent.capture());
        assertTrue(sent.getAllValues().getFirst().getText().contains("шестизначный код"));
        assertEquals("Сайт и Telegram привязаны к одному аккаунту.", sent.getValue().getText());
    }

    private String codeFrom(String bearer) throws Exception {
        var response = mvc.perform(post("/api/auth/link-telegram").header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
                .andExpect(status().isOk()).andReturn().getResponse();
        return new ObjectMapper().readTree(response.getContentAsString()).get("code").asText();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(postJson("/api/auth/login", Map.of("email", email, "password", password)));
    }

    private Cookie loginCookie(String email, String password) throws Exception {
        return login(email, password).andExpect(status().isOk()).andReturn().getResponse().getCookie(REFRESH_COOKIE);
    }

    private ResultActions refresh(Cookie token) throws Exception {
        return mvc.perform(post("/api/auth/refresh").header(CSRF_HEADER, "1").cookie(token));
    }

    private MockHttpServletRequestBuilder postJson(String path, Map<String, String> body) throws Exception {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(body));
    }

    private void register(String email, String password, String referralCode) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(payload(email, password, referralCode)))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String token) throws Exception {
        return mvc.perform(post("/api/auth/confirm-email").contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(Map.of("token", token))));
    }

    private org.springframework.test.web.servlet.ResultActions resend(String email) throws Exception {
        return mvc.perform(post("/api/auth/resend-confirmation").contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(Map.of("email", email))));
    }

    private String lastToken() {
        return lastMailToken("confirm-email");
    }

    private String lastMailToken(String path) {
        var messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, atLeastOnce()).send(messages.capture());
        var message = messages.getAllValues().getLast();
        assertEquals("noreply@example.com", message.getFrom());
        var matcher = Pattern.compile("https://example.com/" + path + "#token=(" + WebTokens.FORMAT + ")")
                .matcher(message.getText());
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private static String payload(String email, String password, String referralCode) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("email", email);
        data.put("password", password);
        data.put("referralCode", referralCode);
        return new ObjectMapper().writeValueAsString(data);
    }
}
