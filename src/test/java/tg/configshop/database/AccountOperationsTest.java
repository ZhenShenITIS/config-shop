package tg.configshop.database;

import tg.configshop.external_api.pay.dto.CreatePaymentResponse;

import tg.configshop.exceptions.subscription.InsufficientBalanceException;
import tg.configshop.exceptions.promocode.PromoCodeEndedException;
import tg.configshop.exceptions.promocode.PromoCodeAlreadyUsedException;

import jakarta.persistence.EntityManagerFactory;
import liquibase.*;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tg.configshop.constants.*;
import tg.configshop.dto.WithdrawalContext;
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

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
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
    private Purchase purchase(BotUser user, long amount) {
        return Purchase.builder().account(user.getAccount()).remnawaveSubscription(user.getSubscription())
                .paidAmount(amount).purchaseType(PurchaseType.DEVICE).deviceCount(1).build();
    }
    @Test void adminCorrectionsCanOverdrawAndRestoreBalance() {
        var users = bean(UserService.class);
        users.addToBalanceAsAdminTopUp(10L, 100L);
        users.addToBalanceAsAdminTopUp(10L, -150L);
        assertEquals(-50, users.getBalance(10L));
        users.addToBalanceAsAdminTopUp(10L, -25L);
        assertEquals(-75, users.getBalance(10L));
        assertEquals(0L, bean(ReferralService.class).getAvailableSumToWithdraw(10L));
        assertThrows(tg.configshop.exceptions.subscription.InsufficientBalanceException.class,
                () -> balance.purchase(purchase(buyer, 1)));
        users.addToBalanceAsAdminTopUp(10L, 125L);
        assertEquals(50, users.getBalance(10L));
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
    @Test void newPaymentsPersistOwnerAndSeparateProviderId() {
        when(bean(PlategaClient.class).createPayment(any())).thenReturn(
            new CreatePaymentResponse("provider-id",PaymentMethod.SBP,"https://example.invalid/pay",
                null,"1000.00 RUB",PaymentStatus.PENDING,null,null,null,null));
        var p = bean(PaymentService.class).createPlategaPayment(1000L, 2, 10L);
        var loaded = bean(PlategaPaymentRepository.class).findById(p.getId()).orElseThrow();
        assertEquals("provider-id", loaded.getTransactionId());
        assertEquals(buyer.getAccount().getId(), loaded.getAccount().getId());
        assertEquals(PaymentMethod.SBP, loaded.getPaymentMethod());
        assertEquals(0, loaded.getAmount().compareTo(new BigDecimal("1000.00")));
    }
    @Test void paymentAndReferralRewardsAreAtomicAndNotRepeated() {
        var first = user(20); var second = user(30); var third = user(40);
        var referrals = bean(ReferralService.class);
        referrals.createReferral(20L, 10L); referrals.createReferral(30L, 20L); referrals.createReferral(40L, 30L);
        var p = payment(buyer, "payment");
        when(bean(PlategaClient.class).updateStatus("payment")).thenReturn(PaymentStatus.CONFIRMED);
        assertEquals(PaymentResult.CONFIRMED, bean(PaymentService.class).checkPayment("payment", 10L));
        assertEquals(PaymentResult.EXPIRED, bean(PaymentService.class).checkPayment("payment", 10L));
        context.publishEvent(new PaymentConfirmedEvent(this, 10L, 1000L, p.getId()));
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
        assertThrows(IllegalArgumentException.class, () -> bean(PaymentService.class).checkPayment("foreign", 10L));
        assertEquals(PaymentResult.MANUAL_REVIEW, bean(PaymentService.class).checkPayment("old-pending", 10L));
        assertEquals(0, bean(BalanceLogRepository.class).count());
        verifyNoInteractions(bean(PlategaClient.class));
    }
    @Test void historicallyCreditedPendingPaymentCannotBeCreditedAgain() {
        var p = payment(buyer, "old-credited");
        balance.credit(TopUp.builder().account(buyer.getAccount()).value(1000L)
                .topUpSource(TopUpSource.EXTERNAL).platega(p).build());
        assertEquals(PaymentResult.EXPIRED, bean(PaymentService.class).checkPayment("old-credited", 10L));
        assertEquals(1000, balance.balance(buyer.getAccount().getId()));
        assertEquals(1, bean(BalanceLogRepository.class).count());
        assertEquals(PaymentStatus.PENDING, bean(PlategaPaymentRepository.class).findById(p.getId()).orElseThrow().getStatus());
        verifyNoInteractions(bean(PlategaClient.class));
    }
    @Test void fractionalPaymentDoesNotTruncateOrPartiallyConfirm() {
        var p = payment(buyer, "fractional"); p.setAmount(new BigDecimal("1000.50"));
        bean(PlategaPaymentRepository.class).saveAndFlush(p);
        when(bean(PlategaClient.class).updateStatus("fractional")).thenReturn(PaymentStatus.CONFIRMED);
        assertThrows(ArithmeticException.class, () -> bean(PaymentService.class).checkPayment("fractional", 10L));
        assertEquals(PaymentStatus.PENDING, bean(PlategaPaymentRepository.class).findById(p.getId()).orElseThrow().getStatus());
        assertEquals(0, bean(BalanceLogRepository.class).count());
    }
    @Test void promoCodeUseAndBalanceAreCommittedTogetherOnce() {
        bean(PromoCodeRepository.class).saveAndFlush(PromoCode.builder().code("PROMO").amount(100L).maxUses(2).build());
        var promo = bean(PromoCodeService.class);
        promo.activatePromoCode("promo", 10L);
        assertThrows(PromoCodeAlreadyUsedException.class, () -> promo.activatePromoCode("PROMO", 10L));
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
                try { bean(PromoCodeService.class).activatePromoCode("LAST", id); return true; }
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
        balance.credit(TopUp.builder().account(buyer.getAccount()).value(200L).topUpSource(TopUpSource.REFERRAL).referral(source.getAccount()).build());
        var input = new WithdrawalContext(); input.setAmount(100); input.setRequisites("test"); input.setWithdrawalType(WithdrawalType.CARD);
        var service = bean(WithdrawalService.class);
        var w = service.createWithdrawal(10, input);
        jdbc.update("insert into migration_id_map(entity,old_id,new_id) values ('withdrawals','123',?)", w.getId());
        assertEquals(w.getId(), service.getWithdrawal("123").getId());
        service.rejectWithdrawal("123");
        assertThrows(IllegalStateException.class, () -> service.rejectWithdrawal(w.getId().toString()));
        assertThrows(IllegalStateException.class, () -> service.approveWithdrawal("123"));
        assertEquals(200, balance.balance(buyer.getAccount().getId()));
        assertEquals(3, bean(BalanceLogRepository.class).count());
    }
    @Test void historyAndReferralProjectionUseNewSchemaWithoutPanelCalls() {
        var referrer = user(20);
        bean(ReferralService.class).createReferral(20L, 10L);
        credit(buyer, 100);
        balance.purchase(purchase(buyer,30));
        var page = bean(OperationsService.class).getOperationsByUserId(10,0);
        assertEquals(2, page.getTotalElements());
        assertEquals(PurchaseType.DEVICE, page.getContent().getFirst().getPurchaseType());
        assertEquals(1, bean(ReferralService.class).getReferralsWithProfitAndLevel(20L,0).getTotalElements());
        verifyNoInteractions(bean(PlategaClient.class));
    }

    @org.springframework.boot.test.context.TestConfiguration @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = {"tg.configshop.repositories", "tg.configshop.external_api.pay.repositories"})
    @Import({BalanceServiceImpl.class, UserServiceImpl.class, ReferralServiceImpl.class, PromoCodeServiceImpl.class,
            WithdrawalService.class, PaymentServiceImpl.class, PaymentConfirmedListener.class, OperationsService.class})
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
        @Bean PlategaClient plategaClient() { return mock(PlategaClient.class); }
    }
}
