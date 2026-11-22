package tg.configshop.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.model.Account;
import tg.configshop.model.EmailConfirmation;
import tg.configshop.model.PromoCode;
import tg.configshop.quartz.services.SchedulerService;
import tg.configshop.repositories.*;
import tg.configshop.services.BalanceService;
import tg.configshop.services.ReferralService;
import tg.configshop.web.registration.RegistrationRateLimiter;

import java.time.Clock;
import java.time.Instant;
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
        doThrow(new tg.configshop.web.registration.WebRegistrationException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "rate_limit_exceeded", "Попробуйте позже."))
                .when(limits).register(anyString());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(payload("user@example.com", PASSWORD, null)))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error").value("rate_limit_exceeded"));
        assertEquals(0, accounts.count());
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
        var messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, atLeastOnce()).send(messages.capture());
        var message = messages.getAllValues().getLast();
        assertEquals("noreply@example.com", message.getFrom());
        var matcher = Pattern.compile("https://example.com/confirm-email#token=([A-Za-z0-9_-]{43})").matcher(message.getText());
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
