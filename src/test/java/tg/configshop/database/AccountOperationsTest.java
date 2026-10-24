package tg.configshop.database;

import tg.configshop.external_api.pay.dto.CreatePaymentResponse;
import tg.configshop.external_api.pay.dto.PaymentStatusResponse;

import tg.configshop.exceptions.subscription.InsufficientBalanceException;
import tg.configshop.exceptions.promocode.PromoCodeEndedException;
import tg.configshop.exceptions.promocode.PromoCodeAlreadyUsedException;

import jakarta.persistence.EntityManagerFactory;
import liquibase.*;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.*;
import tg.configshop.dto.WithdrawalRequest;
import tg.configshop.telegram.TelegramUserService;
import tg.configshop.events.PaymentConfirmedEvent;
import tg.configshop.external_api.pay.*;
import tg.configshop.external_api.pay.constants.*;
import tg.configshop.external_api.pay.model.PlategaPayment;
import tg.configshop.external_api.pay.repositories.PlategaPaymentRepository;
import tg.configshop.listeners.PaymentConfirmedListener;
import tg.configshop.model.*;
import tg.configshop.repositories.*;
import tg.configshop.services.*;
import tg.configshop.services.impl.*;
import tg.configshop.telegram.callbacks.impl.referrals.ReferralCallback;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
class AccountOperationsTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private BalanceService balance;
    private BotUser buyer;

    @BeforeEach void setup() throws Exception {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("drop schema public cascade");
        jdbc.execute("create schema public");
        var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(ds.getConnection()));
        try (var lb = new Liquibase("db/changelog/db.changelog-master.yaml", new ClassLoaderResourceAccessor(), db)) {
            lb.update(new Contexts(), new LabelExpression());
        }
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> ds);
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("prices", Map.ofEntries(
                Map.entry("MIN_DEVICE_COUNT", 1), Map.entry("MAX_DEVICE_COUNT", 10), Map.entry("TRAFFIC_LIMIT_PER_DEVICE", 10),
                Map.entry("BASE_PRICE_FOR_1_MONTH", 100), Map.entry("BASE_PRICE_FOR_3_MONTH", 300), Map.entry("BASE_PRICE_FOR_6_MONTH", 600), Map.entry("BASE_PRICE_FOR_12_MONTH", 1200),
                Map.entry("EXTRA_PER_DEVICE_1_MONTH", 10), Map.entry("EXTRA_PER_DEVICE_3_MONTH", 30), Map.entry("EXTRA_PER_DEVICE_6_MONTH", 60), Map.entry("EXTRA_PER_DEVICE_12_MONTH", 120),
                Map.entry("TRAFFIC_PACKAGE_10_PRICE", 10), Map.entry("TRAFFIC_PACKAGE_30_PRICE", 30), Map.entry("TRAFFIC_PACKAGE_50_PRICE", 50),
                Map.entry("TRAFFIC_PACKAGE_100_PRICE", 100), Map.entry("TRAFFIC_PACKAGE_250_PRICE", 250), Map.entry("TRAFFIC_PACKAGE_500_PRICE", 500))));
        context.register(Config.class);
        context.refresh();
        balance = bean(BalanceService.class);
        buyer = user(10);
    }
    @AfterEach void close() { if (context != null) context.close(); }
    private <T> T bean(Class<T> type) { return context.getBean(type); }
    private BotUser user(long telegramId) {
        var account = bean(AccountRepository.class).saveAndFlush(Account.builder().build());
        bean(RemnawaveSubscriptionRepository.class).saveAndFlush(RemnawaveSubscription.builder()
                .account(account).remnawaveId(telegramId).build());
        bean(BotUserRepository.class).saveAndFlush(BotUser.builder().telegramId(telegramId).name("User " + telegramId).account(account).build());
        return bean(BotUserRepository.class).findById(telegramId).orElseThrow();
    }
    private void credit(BotUser user, long amount) {
        balance.credit(TopUp.builder().account(user.getAccount()).value(amount).topUpSource(TopUpSource.ADMIN).build());
    }
    private UUID accountId(long telegramId) { return bean(BotUserRepository.class).findById(telegramId).orElseThrow().getAccount().getId(); }
    private Purchase purchase(BotUser user, long amount) {
        return Purchase.builder().account(user.getAccount()).remnawaveSubscription(user.getSubscription())
                .paidAmount(amount).purchaseType(PurchaseType.DEVICE).deviceCount(1).build();
    }
    @Test void adminCorrectionsCanOverdrawAndRestoreBalance() {
        var users = balance;
        users.adjustAsAdmin(accountId(10), 100L);
        users.adjustAsAdmin(accountId(10), -150L);
        assertEquals(-50, users.balance(accountId(10)));
        users.adjustAsAdmin(accountId(10), -25L);
        assertEquals(-75, users.balance(accountId(10)));
        assertEquals(0L, bean(ReferralService.class).getAvailableSumToWithdraw(accountId(10)));
        assertThrows(tg.configshop.exceptions.subscription.InsufficientBalanceException.class,
                () -> balance.purchase(purchase(buyer, 1)));
        users.adjustAsAdmin(accountId(10), 125L);
        assertEquals(50, users.balance(accountId(10)));
        assertEquals(List.of(-150L, -25L, 100L, 125L), jdbc.queryForList(
                "select amount from balance_log where type='ADMIN' order by amount", Long.class));
        assertEquals(4, bean(TopUpRepository.class).count());
        assertEquals(4, bean(BalanceLogRepository.class).count());
        assertEquals(0, bean(PurchaseRepository.class).count());
    }
    @Test void negativeNonAdminCreditsAreRejected() {
        for (var source : List.of(TopUpSource.EXTERNAL, TopUpSource.PROMO_CODE, TopUpSource.REFERRAL)) {
            assertThrows(IllegalArgumentException.class, () -> balance.credit(TopUp.builder()
                    .account(buyer.getAccount()).value(-100L).topUpSource(source).build()));
        }
        assertEquals(0, balance.balance(buyer.getAccount().getId()));
        assertEquals(0, bean(TopUpRepository.class).count());
        assertEquals(0, bean(BalanceLogRepository.class).count());
    }
    @Test void recordsSourcesOnceAndRejectsForeignSubscription() {
        credit(buyer, 100);
        var p = purchase(buyer, 30);
        balance.purchase(p);
        balance.purchase(p);
        assertEquals(70, balance.balance(buyer.getAccount().getId()));
        assertEquals(1, bean(PurchaseRepository.class).count());
        var foreign = user(20);
        var invalid = purchase(buyer, 10);
        invalid.setRemnawaveSubscription(foreign.getSubscription());
        assertThrows(IllegalArgumentException.class, () -> balance.purchase(invalid));
        assertEquals(70, balance.balance(buyer.getAccount().getId()));
    }
    @Test void concurrentPurchasesCannotOverspend() throws Exception {
        credit(buyer, 100);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task = () -> {
                start.await();
                try { balance.purchase(purchase(buyer, 70)); return true; }
                catch (InsufficientBalanceException e) { return false; }
            };
            var a = pool.submit(task); var b = pool.submit(task); start.countDown();
            assertNotEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
        assertEquals(30, balance.balance(buyer.getAccount().getId()));
        assertEquals(1, bean(PurchaseRepository.class).count());
    }
    private PlategaPayment payment(BotUser user, String id) {
        return bean(PlategaPaymentRepository.class).saveAndFlush(PlategaPayment.builder()
                .account(user.getAccount()).transactionId(id).amount(new BigDecimal("1000.00"))
                .currency("RUB").paymentMethod(PaymentMethod.SBP).status(PaymentStatus.PENDING).build());
    }
    private static PaymentStatusResponse confirmedPayment(String id) {
        return new PaymentStatusResponse(id, PaymentStatus.CONFIRMED,
                new PaymentStatusResponse.Details(new BigDecimal("1000"), "RUB"));
    }
    @Test void newPaymentsPersistOwnerAndSeparateProviderId() {
        when(bean(PlategaClient.class).createPayment(any())).thenReturn(
            new CreatePaymentResponse("provider-id",PaymentMethod.SBP,"https://example.invalid/pay",
                null,"1000.00 RUB",PaymentStatus.PENDING,null,null,null,null));
        var p = bean(PaymentService.class).createPlategaPayment(1000L, 2, accountId(10), "https://example.invalid/return", "https://example.invalid/fail");
        var loaded = bean(PlategaPaymentRepository.class).findById(p.id()).orElseThrow();
        assertEquals("provider-id", loaded.getTransactionId());
        assertEquals(buyer.getAccount().getId(), loaded.getAccount().getId());
        assertEquals(PaymentMethod.SBP, loaded.getPaymentMethod());
        assertEquals(0, loaded.getAmount().compareTo(new BigDecimal("1000.00")));
    }
    @Test void paymentAndReferralRewardsAreAtomicAndNotRepeated() {
        var first = user(20); var second = user(30); var third = user(40);
        var referrals = bean(ReferralService.class);
        referrals.createReferral(accountId(20), accountId(10)); referrals.createReferral(accountId(30), accountId(20)); referrals.createReferral(accountId(40), accountId(30));
        var p = payment(buyer, "payment");
        when(bean(PlategaClient.class).getPayment("payment")).thenReturn(confirmedPayment("payment"));
        assertEquals(PaymentResult.CONFIRMED, bean(PaymentService.class).checkPayment("payment", accountId(10)));
        assertEquals(PaymentResult.CONFIRMED, bean(PaymentService.class).checkPayment("payment", accountId(10)));
        context.publishEvent(new PaymentConfirmedEvent(accountId(10), 1000L, p.getId()));
        assertEquals(1000, balance.balance(buyer.getAccount().getId()));
        assertEquals(350, balance.balance(first.getAccount().getId()));
        assertEquals(200, balance.balance(second.getAccount().getId()));
        assertEquals(100, balance.balance(third.getAccount().getId()));
        assertEquals(4, bean(TopUpRepository.class).count());
        assertEquals(4, bean(BalanceLogRepository.class).count());
    }
    @Test void paymentOwnershipAndUnmigratedPaymentAreHandledWithoutCredit() {
        var other = user(20);
        payment(other, "foreign");
        assertThrows(IllegalArgumentException.class, () -> bean(PaymentService.class).checkPayment("foreign", accountId(10)));
        assertEquals(PaymentResult.MANUAL_REVIEW, bean(PaymentService.class).checkPayment("old-pending", accountId(10)));
        assertEquals(0, bean(BalanceLogRepository.class).count());
        verifyNoInteractions(bean(PlategaClient.class));
    }
    @Test void historicallyCreditedPendingPaymentCannotBeCreditedAgain() {
        var p = payment(buyer, "old-credited");
        balance.credit(TopUp.builder().account(buyer.getAccount()).value(1000L)
                .topUpSource(TopUpSource.EXTERNAL).platega(p).build());
        assertEquals(PaymentResult.CONFIRMED, bean(PaymentService.class).checkPayment("old-credited", accountId(10)));
        assertEquals(1000, balance.balance(buyer.getAccount().getId()));
        assertEquals(1, bean(BalanceLogRepository.class).count());
        assertEquals(PaymentStatus.PENDING, bean(PlategaPaymentRepository.class).findById(p.getId()).orElseThrow().getStatus());
        bean(PaymentPollingService.class).checkPendingPayments();
        verifyNoInteractions(bean(PlategaClient.class));
    }
    @Test void providerRequestRunsWithoutTransactionOrPaymentLockEvenForTransactionalCaller() {
        payment(buyer, "outside-transaction");
        UUID owner = buyer.getAccount().getId();
        var events = new AtomicInteger();
        context.addApplicationListener(event -> {
            if (event instanceof org.springframework.context.PayloadApplicationEvent<?> payload
                    && payload.getPayload() instanceof PaymentConfirmedEvent) {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                events.incrementAndGet();
            }
        });
        when(bean(PlategaClient.class).getPayment("outside-transaction")).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("outside-transaction", jdbc.queryForObject(
                    "SELECT provider_payment_id FROM platega_payments WHERE provider_payment_id=? FOR UPDATE NOWAIT",
                    String.class, "outside-transaction"));
            return confirmedPayment("outside-transaction");
        });
        new TransactionTemplate(bean(PlatformTransactionManager.class)).execute(tx -> {
            assertEquals(PaymentResult.CONFIRMED, bean(PaymentService.class).checkPayment("outside-transaction", owner));
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        });
        assertEquals(1000, balance.balance(owner));
        assertEquals(1, events.get());
    }

    @Test void concurrentManualAndBackgroundChecksCreditPaymentAndReferralsOnce() throws Exception {
        var referrer = user(20);
        UUID owner = buyer.getAccount().getId();
        bean(ReferralService.class).createReferral(referrer.getAccount().getId(), owner);
        payment(buyer, "concurrent-payment");
        var bothRequests = new CyclicBarrier(2);
        var events = new AtomicInteger();
        context.addApplicationListener(event -> {
            if (event instanceof org.springframework.context.PayloadApplicationEvent<?> payload
                    && payload.getPayload() instanceof PaymentConfirmedEvent) events.incrementAndGet();
        });
        when(bean(PlategaClient.class).getPayment("concurrent-payment")).thenAnswer(invocation -> {
            bothRequests.await(10, TimeUnit.SECONDS);
            return confirmedPayment("concurrent-payment");
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var manual = pool.submit(() -> bean(PaymentService.class).checkPayment("concurrent-payment", owner));
            var background = pool.submit(() -> bean(PaymentPollingService.class).checkPendingPayments());
            assertEquals(PaymentResult.CONFIRMED, manual.get(15, TimeUnit.SECONDS));
            background.get(15, TimeUnit.SECONDS);
        }
        assertEquals(1000, balance.balance(owner));
        assertEquals(350, balance.balance(referrer.getAccount().getId()));
        assertEquals(2, bean(TopUpRepository.class).count());
        assertEquals(2, bean(BalanceLogRepository.class).count());
        assertEquals(1, events.get());
    }

    @Test void latePendingResponseDoesNotOverwriteConfirmedPayment() throws Exception {
        var payment = payment(buyer, "late-pending");
        UUID owner = buyer.getAccount().getId();
        var pendingStarted = new CountDownLatch(1);
        var releasePending = new CountDownLatch(1);
        var calls = new AtomicInteger();
        when(bean(PlategaClient.class).getPayment("late-pending")).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                pendingStarted.countDown();
                assertTrue(releasePending.await(10, TimeUnit.SECONDS));
                return new PaymentStatusResponse("late-pending", PaymentStatus.PENDING, confirmedPayment("late-pending").paymentDetails());
            }
            return confirmedPayment("late-pending");
        });
        try (var pool = Executors.newSingleThreadExecutor()) {
            var late = pool.submit(() -> bean(PaymentService.class).checkPayment("late-pending", owner));
            try {
                assertTrue(pendingStarted.await(10, TimeUnit.SECONDS));
                assertEquals(PaymentResult.CONFIRMED, bean(PaymentService.class).checkPayment("late-pending", owner));
            } finally {
                releasePending.countDown();
            }
            assertEquals(PaymentResult.CONFIRMED, late.get(15, TimeUnit.SECONDS));
        }
        assertEquals(PaymentStatus.CONFIRMED, bean(PlategaPaymentRepository.class).findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(1000, balance.balance(owner));
        assertEquals(1, bean(BalanceLogRepository.class).count());
    }

    static Stream<PaymentStatusResponse> invalidPaymentResponses() {
        return Stream.of(null,
                new PaymentStatusResponse("wrong-id", PaymentStatus.CONFIRMED, confirmedPayment("invalid").paymentDetails()),
                new PaymentStatusResponse("invalid", PaymentStatus.CONFIRMED, new PaymentStatusResponse.Details(new BigDecimal("999"), "RUB")),
                new PaymentStatusResponse("invalid", PaymentStatus.CONFIRMED, new PaymentStatusResponse.Details(new BigDecimal("1000"), "USD")),
                new PaymentStatusResponse("invalid", PaymentStatus.CONFIRMED, new PaymentStatusResponse.Details(null, "RUB")),
                new PaymentStatusResponse("invalid", PaymentStatus.CONFIRMED, null),
                new PaymentStatusResponse("invalid", null, confirmedPayment("invalid").paymentDetails()));
    }

    @ParameterizedTest @MethodSource("invalidPaymentResponses")
    void invalidProviderResponseCannotConfirmOrCreditPayment(PaymentStatusResponse response) {
        var payment = payment(buyer, "invalid");
        when(bean(PlategaClient.class).getPayment("invalid")).thenReturn(response);
        assertEquals(PaymentResult.MANUAL_REVIEW, bean(PaymentService.class).checkPayment("invalid", buyer.getAccount().getId()));
        assertEquals(PaymentStatus.PENDING, bean(PlategaPaymentRepository.class).findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(0, bean(TopUpRepository.class).count());
        assertEquals(0, bean(BalanceLogRepository.class).count());
    }

    @ParameterizedTest @EnumSource(value = PaymentStatus.class, names = {"CANCELED", "CHARGEBACKED"})
    void terminalUnpaidStatusIsSavedWithoutCreditAndRepeatedRequest(PaymentStatus status) {
        var payment = payment(buyer, "unpaid");
        when(bean(PlategaClient.class).getPayment("unpaid"))
                .thenReturn(new PaymentStatusResponse("unpaid", status, confirmedPayment("unpaid").paymentDetails()));
        PaymentResult expected = status == PaymentStatus.CANCELED ? PaymentResult.CANCELED : PaymentResult.MANUAL_REVIEW;
        assertEquals(expected, bean(PaymentService.class).checkPayment("unpaid", buyer.getAccount().getId()));
        assertEquals(expected, bean(PaymentService.class).checkPayment("unpaid", buyer.getAccount().getId()));
        assertEquals(status, bean(PlategaPaymentRepository.class).findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(0, bean(BalanceLogRepository.class).count());
        verify(bean(PlategaClient.class)).getPayment("unpaid");
    }

    @Test void pollingContinuesAfterProviderFailureAndRetriesUnfinishedPaymentFromDatabase() {
        payment(buyer, "failed-request");
        payment(buyer, "still-pending");
        payment(buyer, "paid-request");
        var client = bean(PlategaClient.class);
        when(client.getPayment("failed-request")).thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(confirmedPayment("failed-request"));
        when(client.getPayment("still-pending")).thenReturn(new PaymentStatusResponse(
                "still-pending", PaymentStatus.PENDING, confirmedPayment("still-pending").paymentDetails()));
        when(client.getPayment("paid-request")).thenReturn(confirmedPayment("paid-request"));
        bean(PaymentPollingService.class).checkPendingPayments();
        assertEquals(1000, balance.balance(buyer.getAccount().getId()));
        new PaymentPollingService(bean(PlategaPaymentRepository.class), bean(PaymentService.class)).checkPendingPayments();
        assertEquals(2000, balance.balance(buyer.getAccount().getId()));
        assertEquals(2, bean(BalanceLogRepository.class).count());
        verify(client, times(2)).getPayment("failed-request");
        verify(client, times(2)).getPayment("still-pending");
        verify(client).getPayment("paid-request");
    }

    @Test void pollingDoesNotSkipPaymentsWhenConfirmedRowsDisappearBetweenPages() {
        jdbc.update("""
                INSERT INTO platega_payments(provider_payment_id, account_id, amount, currency, status, payment_method, created_at)
                SELECT 'paged-' || n, ?, 1000, 'RUB', 'PENDING', 'SBPQR', '2026-01-01T00:00:00Z'
                FROM generate_series(1, 105) n
                """, buyer.getAccount().getId());
        when(bean(PlategaClient.class).getPayment(startsWith("paged-")))
                .thenAnswer(invocation -> confirmedPayment(invocation.getArgument(0)));
        bean(PaymentPollingService.class).checkPendingPayments();
        assertEquals(105000, balance.balance(buyer.getAccount().getId()));
        assertEquals(105, bean(TopUpRepository.class).count());
        assertEquals(105, bean(BalanceLogRepository.class).count());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM platega_payments WHERE status='PENDING'", Integer.class));
        verify(bean(PlategaClient.class), times(105)).getPayment(anyString());
    }

    @Test void failedSettlementDoesNotStopPollingAndIsRetriedOnNextRun() {
        var failed = payment(buyer, "failed-settlement");
        payment(buyer, "successful-settlement");
        var failOnce = new AtomicBoolean(true);
        context.addApplicationListener(event -> {
            if (event instanceof org.springframework.context.PayloadApplicationEvent<?> payload
                    && payload.getPayload() instanceof PaymentConfirmedEvent confirmed
                    && confirmed.paymentId().equals(failed.getId()) && failOnce.getAndSet(false)) {
                throw new IllegalStateException("settlement failure");
            }
        });
        when(bean(PlategaClient.class).getPayment(anyString()))
                .thenAnswer(invocation -> confirmedPayment(invocation.getArgument(0)));
        bean(PaymentPollingService.class).checkPendingPayments();
        assertEquals(1000, balance.balance(buyer.getAccount().getId()));
        assertEquals(PaymentStatus.PENDING, bean(PlategaPaymentRepository.class).findById(failed.getId()).orElseThrow().getStatus());
        assertEquals(1, bean(BalanceLogRepository.class).count());
        bean(PaymentPollingService.class).checkPendingPayments();
        assertEquals(2000, balance.balance(buyer.getAccount().getId()));
        assertEquals(2, bean(TopUpRepository.class).count());
        assertEquals(2, bean(BalanceLogRepository.class).count());
    }

    @ParameterizedTest @ValueSource(strings = {"120", "date", "invalid"})
    void rateLimitPausesBothManualAndBackgroundProviderRequests(String retryAfter) {
        payment(buyer, "rate-limited");
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, retryAfter.equals("date")
                ? ZonedDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(2).format(DateTimeFormatter.RFC_1123_DATE_TIME) : retryAfter);
        when(bean(PlategaClient.class).getPayment("rate-limited")).thenThrow(HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "rate limited", headers, new byte[0], java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(PaymentResult.PROCESSING, bean(PaymentService.class).checkPayment("rate-limited", buyer.getAccount().getId()));
        bean(PaymentPollingService.class).checkPendingPayments();
        assertEquals(PaymentResult.PROCESSING, bean(PaymentService.class).checkPayment("rate-limited", buyer.getAccount().getId()));
        verify(bean(PlategaClient.class)).getPayment("rate-limited");
        assertEquals(0, bean(BalanceLogRepository.class).count());
    }

    @Test void fractionalPaymentDoesNotTruncateOrPartiallyConfirm() {
        var p = payment(buyer, "fractional"); p.setAmount(new BigDecimal("1000.50"));
        bean(PlategaPaymentRepository.class).saveAndFlush(p);
        when(bean(PlategaClient.class).getPayment("fractional")).thenReturn(new PaymentStatusResponse("fractional", PaymentStatus.CONFIRMED,
                new PaymentStatusResponse.Details(new BigDecimal("1000.50"), "RUB")));
        assertThrows(ArithmeticException.class, () -> bean(PaymentService.class).checkPayment("fractional", accountId(10)));
        assertEquals(PaymentStatus.PENDING, bean(PlategaPaymentRepository.class).findById(p.getId()).orElseThrow().getStatus());
        assertEquals(0, bean(BalanceLogRepository.class).count());
    }
    @Test void promoCodeUseAndBalanceAreCommittedTogetherOnce() {
        bean(PromoCodeRepository.class).saveAndFlush(PromoCode.builder().code("PROMO").amount(100L).maxUses(2).build());
        var promo = bean(PromoCodeService.class);
        promo.activatePromoCode("promo", accountId(10));
        assertThrows(PromoCodeAlreadyUsedException.class, () -> promo.activatePromoCode("PROMO", accountId(10)));
        assertEquals(100, balance.balance(buyer.getAccount().getId()));
        assertEquals(1, bean(PromoCodeUseRepository.class).count());
        assertEquals(1, bean(PromoCodeRepository.class).findByCode("PROMO").orElseThrow().getCurrentUses());
    }
    @Test void concurrentLastPromoUseIsGrantedOnce() throws Exception {
        user(20);
        bean(PromoCodeRepository.class).saveAndFlush(PromoCode.builder().code("LAST").amount(100L).maxUses(1).build());
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = new ArrayList<Future<Boolean>>();
            for (long id : new long[]{10,20}) tasks.add(pool.submit(() -> {
                start.await();
                try { bean(PromoCodeService.class).activatePromoCode("LAST", accountId(id)); return true; }
                catch (PromoCodeEndedException e) { return false; }
            }));
            start.countDown();
            assertNotEquals(tasks.get(0).get(15,TimeUnit.SECONDS), tasks.get(1).get(15,TimeUnit.SECONDS));
        }
        assertEquals(1, bean(BalanceLogRepository.class).count());
        assertEquals(1, bean(PromoCodeUseRepository.class).count());
    }
    @Test void withdrawalRefundIsOnceAndLegacyButtonResolvesUuid() {
        var source = user(20);
        balance.credit(TopUp.builder().account(buyer.getAccount()).value(2000L).topUpSource(TopUpSource.REFERRAL).referral(source.getAccount()).build());
        var input = new WithdrawalRequest(WithdrawalType.CARD, 1000, "test");
        var service = bean(WithdrawalService.class);
        var w = service.createWithdrawal(accountId(10), input);
        jdbc.update("insert into migration_id_map(entity,old_id,new_id) values ('withdrawals','123',?)", w.getId());
        assertEquals(w.getId(), service.getWithdrawal("123").getId());
        service.rejectWithdrawal("123");
        assertThrows(IllegalStateException.class, () -> service.rejectWithdrawal(w.getId().toString()));
        assertThrows(IllegalStateException.class, () -> service.approveWithdrawal("123"));
        assertEquals(2000, balance.balance(buyer.getAccount().getId()));
        assertEquals(3, bean(BalanceLogRepository.class).count());
    }
    @Test void historyAndReferralProjectionUseNewSchemaWithoutPanelCalls() {
        var referrer = user(20);
        bean(ReferralService.class).createReferral(accountId(20), accountId(10));
        credit(buyer, 100);
        balance.purchase(purchase(buyer,30));
        var page = bean(OperationsService.class).getOperations(accountId(10),0,10);
        assertEquals(2, page.getTotalElements());
        assertEquals(PurchaseType.DEVICE, page.getContent().getFirst().getPurchaseType());
        assertEquals(1, bean(ReferralService.class).getReferralsWithProfitAndLevel(accountId(20),0,15).getTotalElements());
        verifyNoInteractions(bean(PlategaClient.class));
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void referralMenuWorksOnVirtualThreadWithoutCallerTransaction(int codeCount) throws Exception {
        for (int i = 0; i < codeCount; i++) {
            bean(PromoCodeRepository.class).saveAndFlush(PromoCode.builder().code("REF_TEST_" + i)
                    .amount(100L).isReferral(true).referrer(buyer.getAccount()).build());
        }
        var callback = new ReferralCallback(bean(ReferralService.class), new TelegramUserService(bean(BotUserRepository.class), balance, bean(SubscriptionAccessService.class)));
        ReflectionTestUtils.setField(callback, "botUsername", "test_bot");
        var message = mock(Message.class);
        when(message.getChatId()).thenReturn(10L);
        when(message.getMessageId()).thenReturn(1);
        var query = new CallbackQuery();
        query.setFrom(new User(10L, "Test", false));
        query.setMessage(message);
        query.setData(CallbackName.REFERRAL.getCallbackName());
        var telegram = mock(TelegramClient.class);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> {
                assertTrue(Thread.currentThread().isVirtual());
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                callback.processCallback(query, telegram);
            }).get(15, TimeUnit.SECONDS);
        }
        var edit = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegram).execute(edit.capture());
        assertEquals("10", edit.getValue().getChatId());
        assertTrue(edit.getValue().getText().contains("https://t.me/test_bot?start=10"));
        for (int i = 0; i < codeCount; i++) {
            assertTrue(edit.getValue().getText().contains("REF_TEST_" + i));
        }
        verifyNoInteractions(bean(PlategaClient.class));
    }

    @Test void referralCodesCanBeCreatedWithoutCallerTransaction() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        var referrals = bean(ReferralService.class);
        referrals.createReferralCode(accountId(10), "REF_10");
        referrals.createReferralCode(accountId(10), "REF_10");
        bean(PromoCodeService.class).createReferralPromoCode("CUSTOM_REF", accountId(10));
        assertEquals(Set.of("REF_10", "CUSTOM_REF"), new HashSet<>(referrals.getReferralPromoCodes(accountId(10))));
        assertEquals(2, bean(PromoCodeRepository.class).count());
        assertEquals(buyer.getAccount().getId(), bean(PromoCodeRepository.class)
                .findByCode("REF_10").orElseThrow().getReferrer().getId());
    }


    @Test void accountWithoutChannelCanReadBalanceHistoryAndEmptySubscriptions() {
        UUID id = bean(AccountService.class).createAccount();
        assertEquals(0, bean(AccountService.class).getAccount(id).balance());
        assertTrue(bean(SubscriptionAccessService.class).list(id).isEmpty());
        balance.adjustAsAdmin(id, 150);
        assertEquals(150, bean(AccountService.class).getAccount(id).balance());
        assertEquals(1, bean(OperationsService.class).getOperations(id, 0, 20).getTotalElements());
        assertTrue(bean(BotUserRepository.class).findFirstByAccount_IdOrderByTelegramIdAsc(id).isEmpty());
        assertEquals(0, context.getBeanNamesForType(TelegramClient.class).length);
        verifyNoInteractions(bean(tg.configshop.external_api.remnawave.RemnawaveClient.class));
    }

    @Test void accountOnlyPaymentRewardsMixedChainAndProjectionDoesNotDuplicateProfiles() {
        var accounts = bean(AccountService.class);
        UUID payer = accounts.createAccount(), first = accounts.createAccount(), third = accounts.createAccount();
        UUID second = buyer.getAccount().getId();
        var referrals = bean(ReferralService.class);
        referrals.createReferral(first, payer); referrals.createReferral(second, first); referrals.createReferral(third, second);
        var payment = bean(PlategaPaymentRepository.class).saveAndFlush(PlategaPayment.builder()
                .account(accounts.requireAccount(payer)).transactionId("web-payment").amount(new BigDecimal("1000"))
                .currency("RUB").paymentMethod(PaymentMethod.SBP).status(PaymentStatus.PENDING).build());
        when(bean(PlategaClient.class).getPayment("web-payment")).thenReturn(confirmedPayment("web-payment"));
        bean(PaymentPollingService.class).checkPendingPayments();
        assertEquals(PaymentResult.CONFIRMED, bean(PaymentService.class).checkPayment("web-payment", payer));
        context.publishEvent(new PaymentConfirmedEvent(payer, 1000, payment.getId()));
        assertEquals(1000, balance.balance(payer)); assertEquals(350, balance.balance(first));
        assertEquals(200, balance.balance(second)); assertEquals(100, balance.balance(third));
        for (long id : new long[]{101,102}) bean(BotUserRepository.class).saveAndFlush(BotUser.builder()
                .telegramId(id).name("Profile").account(accounts.requireAccount(payer)).build());
        var page = referrals.getReferralsWithProfitAndLevel(first,0,15);
        assertEquals(1,page.getTotalElements()); assertEquals(payer,page.getContent().getFirst().accountId());
        assertEquals(350,page.getContent().getFirst().profit());
    }

    @Test void accountOnlyReferralPromoAndWithdrawalNeedNoProfile() {
        UUID owner = bean(AccountService.class).createAccount(), member = bean(AccountService.class).createAccount();
        bean(PromoCodeService.class).createReferralPromoCode("WEB_REF",owner);
        bean(PromoCodeService.class).activatePromoCode("WEB_REF",member);
        assertEquals(owner,bean(ReferralService.class).getReferrerId(member).orElseThrow());
        assertEquals(100,balance.balance(member));
        balance.credit(TopUp.builder().account(bean(AccountService.class).requireAccount(owner)).value(2000L)
                .topUpSource(TopUpSource.REFERRAL).referral(bean(AccountService.class).requireAccount(member)).build());
        var withdrawal = bean(WithdrawalService.class);
        assertThrows(IllegalStateException.class, () -> withdrawal.createWithdrawal(owner,new WithdrawalRequest(WithdrawalType.CARD,999,"test")));
        var created=withdrawal.createWithdrawal(owner,new WithdrawalRequest(WithdrawalType.CARD,1000,"test"));
        assertEquals(owner,created.accountId()); assertEquals(1000,balance.balance(owner));
        withdrawal.rejectWithdrawal(created.id().toString());
        assertEquals(2000,balance.balance(owner));
    }

    @Test void paymentConstraintsAreEnforcedBeforeProviderCall() {
        UUID id=bean(AccountService.class).createAccount(); var payments=bean(PaymentService.class);
        for(long amount:new long[]{0,99,10001}) assertThrows(IllegalArgumentException.class,
                () -> payments.createPlategaPayment(amount,2,id,"https://example.invalid/ok","https://example.invalid/fail"));
        assertThrows(IllegalArgumentException.class, () -> payments.createPlategaPayment(100L,999,id,"https://example.invalid/ok","https://example.invalid/fail"));
        assertThrows(IllegalArgumentException.class, () -> payments.createPlategaPayment(100L,2,UUID.randomUUID(),"https://example.invalid/ok","https://example.invalid/fail"));
        verifyNoInteractions(bean(PlategaClient.class));
        assertEquals(0,bean(PlategaPaymentRepository.class).count());
    }

    @Test void purchaseChangesOnlySelectedSubscriptionWithoutChannel() {
        UUID id=bean(AccountService.class).createAccount(); var account=bean(AccountService.class).requireAccount(id);
        var first=bean(RemnawaveSubscriptionRepository.class).saveAndFlush(RemnawaveSubscription.builder().account(account).remnawaveId(501L).build());
        var second=bean(RemnawaveSubscriptionRepository.class).saveAndFlush(RemnawaveSubscription.builder().account(account).remnawaveId(502L).build());
        balance.adjustAsAdmin(id,1000);
        var client=bean(tg.configshop.external_api.remnawave.RemnawaveClient.class);
        var expiry=java.time.Instant.now().plusSeconds(86400);
        when(client.getUser(second.remnawaveRef())).thenReturn(tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse.builder()
                .id(502L).expireAt(expiry).hwidDeviceLimit(1).build());
        bean(SubscriptionService.class).buySubscription(id,second.getId(),30,2);
        assertEquals(890,balance.balance(id));
        var purchase=bean(PurchaseRepository.class).findAll().getFirst();
        assertEquals(second.getId(),purchase.getRemnawaveSubscription().getId());
        verify(client).updateSubscription(second.remnawaveRef(),expiry.plusSeconds(30L*86400),2);
        verify(client,never()).getUser(first.remnawaveRef());
        assertEquals(2,bean(SubscriptionAccessService.class).list(id).size());
    }

    @Test void everySubscriptionEntryPointRejectsForeignOwnerBeforeSideEffects() {
        UUID id=bean(AccountService.class).createAccount(), foreign=buyer.getSubscription().getId();
        List<Runnable> attempts=List.of(
                () -> bean(SubscriptionService.class).buySubscription(id,foreign,30,1),
                () -> bean(TrafficPackageService.class).buyTraffic(id,foreign,10),
                () -> bean(DevicePurchaseService.class).purchaseDevices(id,foreign,1),
                () -> bean(DevicePurchaseService.class).preview(id,foreign,1),
                () -> bean(DeviceService.class).getDevices(id,foreign),
                () -> bean(DeviceService.class).deleteDeviceById(id,foreign,"device"),
                () -> bean(ExternalSubscriptionService.class).getExternalUser(id,foreign));
        for(var attempt:attempts) assertThrows(IllegalArgumentException.class,attempt::run);
        verifyNoInteractions(bean(tg.configshop.external_api.remnawave.RemnawaveClient.class));
        assertEquals(0,bean(PurchaseRepository.class).count()); assertEquals(0,bean(BalanceLogRepository.class).count());
    }

    @Test void trialCreationWithoutChannelIsIdempotentAndPreservesOtherAccounts() {
        UUID id=bean(AccountService.class).createAccount(); String key="a_"+id.toString().replace("-","");
        var client=bean(tg.configshop.external_api.remnawave.RemnawaveClient.class);
        when(client.createBasicUser(key)).thenReturn(tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse.builder()
                .id(601L).shortUuid("short-web").expireAt(java.time.Instant.now().plusSeconds(86400)).build());
        var initial=bean(RegistrationService.class).createTrialSubscription(id);
        assertEquals(initial,bean(RegistrationService.class).createTrialSubscription(id));
        assertEquals(id,initial.accountId()); verify(client,times(1)).createBasicUser(key);
        verify(bean(tg.configshop.quartz.services.SchedulerService.class)).scheduleTrialTrafficChecks(id,initial.id());
        assertTrue(bean(BotUserRepository.class).findFirstByAccount_IdOrderByTelegramIdAsc(id).isEmpty());
        assertEquals(1,bean(SubscriptionAccessService.class).list(buyer.getAccount().getId()).size());
    }

    @Test void referralFailureRollsBackPaymentAndEveryLedgerEntry() {
        UUID payer=bean(AccountService.class).createAccount(), referrer=bean(AccountService.class).createAccount();
        bean(ReferralService.class).createReferral(referrer,payer);
        var payment=bean(PlategaPaymentRepository.class).saveAndFlush(PlategaPayment.builder()
                .account(bean(AccountService.class).requireAccount(payer)).transactionId("rollback-payment")
                .amount(new BigDecimal("1000")).currency("RUB").paymentMethod(PaymentMethod.SBP).status(PaymentStatus.PENDING).build());
        when(bean(PlategaClient.class).getPayment("rollback-payment")).thenReturn(confirmedPayment("rollback-payment"));
        var failOnce = new AtomicBoolean(true);
        context.addApplicationListener(event -> {
            if(event instanceof org.springframework.context.PayloadApplicationEvent<?> payload
                    && payload.getPayload() instanceof tg.configshop.events.ReferralRewardEvent
                    && failOnce.getAndSet(false)) throw new IllegalStateException("test failure");
        });
        assertThrows(IllegalStateException.class, () -> bean(PaymentService.class).checkPayment("rollback-payment",payer));
        assertEquals(PaymentStatus.PENDING,bean(PlategaPaymentRepository.class).findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(0,balance.balance(payer)); assertEquals(0,balance.balance(referrer));
        assertEquals(0,bean(TopUpRepository.class).count()); assertEquals(0,bean(BalanceLogRepository.class).count());
        bean(PaymentPollingService.class).checkPendingPayments();
        assertEquals(PaymentStatus.CONFIRMED,bean(PlategaPaymentRepository.class).findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(1000,balance.balance(payer)); assertEquals(350,balance.balance(referrer));
        assertEquals(2,bean(TopUpRepository.class).count()); assertEquals(2,bean(BalanceLogRepository.class).count());
    }

    @org.springframework.boot.test.context.TestConfiguration @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = {"tg.configshop.repositories", "tg.configshop.external_api.pay.repositories"})
    @Import({BalanceServiceImpl.class, AccountService.class, SubscriptionAccessService.class, ReferralServiceImpl.class, PromoCodeServiceImpl.class,
            WithdrawalService.class, PaymentServiceImpl.class, PaymentPollingService.class, PaymentConfirmedListener.class, OperationsService.class,
            SubscriptionServiceImpl.class, TrafficPackageServiceImpl.class, DeviceService.class, DevicePurchaseService.class,
            ExternalSubscriptionService.class, RegistrationServiceImpl.class, tg.configshop.listeners.RemnawaveEventListener.class})
    static class Config {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory = new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(ds);
            factory.setPackagesToScan("tg.configshop.model", "tg.configshop.external_api.pay");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate", "hibernate.physical_naming_strategy",
                    "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl")); return factory;
        }
        @Bean JpaTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean tg.configshop.external_api.remnawave.RemnawaveClient remnawaveClient() { return mock(tg.configshop.external_api.remnawave.RemnawaveClient.class); }
        @Bean tg.configshop.external_api.remnawave.config.RemnawaveApiVersion apiVersion() { return new tg.configshop.external_api.remnawave.config.RemnawaveApiVersion(3); }
        @Bean tg.configshop.quartz.services.SchedulerService scheduler() { return mock(tg.configshop.quartz.services.SchedulerService.class); }
        @Bean PlategaClient plategaClient() { return mock(PlategaClient.class); }
    }
}
