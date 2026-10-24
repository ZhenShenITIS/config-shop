package tg.configshop.database;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class LiquibaseMigrationTest {
    private static final String MASTER = "db/changelog/db.changelog-master.yaml";
    private static final String BASELINE = "db/changelog/db.changelog-baseline.yaml";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
    }

    @Test
    void createsSchemaValidatesEntitiesAndDoesNotRepeatMigration() throws Exception {
        try (Liquibase liquibase = liquibase(MASTER)) {
            liquibase.update(new Contexts(), new LabelExpression());
            liquibase.update(new Contexts(), new LabelExpression());
        }

        assertEquals(34, jdbc.queryForObject("""
                SELECT count(*) FROM pg_tables WHERE schemaname = 'public'
                AND tablename NOT IN ('databasechangelog', 'databasechangeloglock')
                """, Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM bot_users", Integer.class));
        assertEntitiesMatchSchema();
    }

    @Test
    void syncsExistingSchemaWithoutChangingDataAndUsesSameChangesetIdentity() throws Exception {
        createExistingSchema();
        jdbc.update("INSERT INTO promo_codes (code, amount) VALUES ('MIGRATION_TEST', 100)");

        try (Liquibase liquibase = liquibase(BASELINE)) {
            liquibase.changeLogSync(new Contexts(), new LabelExpression());
        }
        try (Liquibase liquibase = liquibase(MASTER)) {
            liquibase.update(new Contexts(), new LabelExpression());
        }

        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Integer.class));
        assertEquals("db/changelog/changes/001-initial-schema.sql",
                jdbc.queryForObject("SELECT filename FROM databasechangelog WHERE id='001-initial-schema'", String.class));
        assertEquals(100L, jdbc.queryForObject("SELECT amount FROM promo_codes", Long.class));
        assertEquals(2L, jdbc.queryForObject(
                "INSERT INTO old_promo_codes (code) VALUES ('AFTER_SYNC') RETURNING id", Long.class));
        assertNotNull(jdbc.queryForObject("INSERT INTO promo_codes(code,amount) VALUES ('NEW',100) RETURNING id", java.util.UUID.class));
        assertEntitiesMatchSchema();
    }

    @Test
    void refusesToInitializeExistingSchemaWithoutExplicitSync() throws Exception {
        createExistingSchema();
        try (Liquibase liquibase = liquibase(MASTER)) {
            assertThrows(LiquibaseException.class,
                    () -> liquibase.update(new Contexts(), new LabelExpression()));
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Integer.class));
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT to_regclass('public.bot_users') IS NOT NULL", Boolean.class)));
    }

    @Test
    void transfersHistoryAndReconcilesEveryBalanceWithPendingPaymentException() throws Exception {
        createExistingSchema();
        jdbc.execute("""
            INSERT INTO bot_users(id,balance,first_name,remnawave_uuid,remnawave_id,created_at)
            VALUES (10,1000,'Buyer','uuid10',100,'2026-01-01T00:00:00Z'),(20,50,'Referrer','uuid20',200,'2026-01-01T00:00:00Z');
            INSERT INTO referrals(referrer_id,referral_id) VALUES(20,10);
            INSERT INTO subscriptions(id,cost,name,duration_days,device_count) VALUES(1,300,'old tariff',30,2);
            INSERT INTO purchases(bot_user_id,paid_amount,purchase_type,subscription_id) VALUES(10,300,'SUBSCRIPTION',1);
            INSERT INTO purchases(bot_user_id,paid_amount,purchase_type,device_count) VALUES(10,50,'DEVICE',1);
            INSERT INTO purchases(bot_user_id,paid_amount,purchase_type,traffic_gb) VALUES(10,100,'TRAFFIC',10);
            INSERT INTO promo_codes(id,code,amount,current_uses,max_uses) VALUES(1,'CODE',100,1,10);
            INSERT INTO promo_code_uses(promo_code_id,bot_user_id) VALUES(1,10);
            INSERT INTO platega_payments(transaction_id,amount,currency,status,payment_method)
            VALUES ('paid',1000,'RUB','PENDING','SBPQR'),('pending',200,'RUB','PENDING','CardRu');
            INSERT INTO top_ups(bot_user_id,value,top_up_source,external_id) VALUES(10,1000,'EXTERNAL','paid'),(10,100,'PROMO_CODE','CODE');
            INSERT INTO top_ups(bot_user_id,value,top_up_source) VALUES(10,-20,'ADMIN');
            INSERT INTO top_ups(bot_user_id,value,top_up_source,referral_id) VALUES(20,40,'REFERRAL',10);
            INSERT INTO withdrawals(id,bot_user_id,amount,status,type,requisites) VALUES(123,10,100,'REJECTED','CARD','test');
            """);
        try (Liquibase lb = liquibase(BASELINE)) { lb.changeLogSync(new Contexts(), new LabelExpression()); }
        try (Liquibase lb = liquibase(MASTER)) { lb.update(new Contexts(), new LabelExpression()); lb.update(new Contexts(), new LabelExpression()); }
        assertEntitiesMatchSchema();
        assertEquals(2, jdbc.queryForObject("select count(*) from accounts", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from remnawave_subscriptions", Integer.class));
        assertEquals(1000L, jdbc.queryForObject("select sum(l.amount) from balance_log l join bot_users b on b.account_id=l.account_id where b.telegram_id=10", Long.class));
        assertEquals(50L, jdbc.queryForObject("select sum(l.amount) from balance_log l join bot_users b on b.account_id=l.account_id where b.telegram_id=20", Long.class));
        assertEquals(370L, jdbc.queryForObject("select correction from migration_report where reason='BALANCE_RECONCILIATION' and old_id='10'", Long.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from migration_report where old_id='pending' and reason='UNRESOLVED_OWNER_MANUAL_REVIEW'", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from old_platega_payments where transaction_id='pending'", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from platega_payments", Integer.class));
        assertEquals(30, jdbc.queryForObject("select sub_days from purchases where type='SUBSCRIPTION'", Integer.class));
        assertEquals(2, jdbc.queryForObject("select sub_devices from purchases where type='SUBSCRIPTION'", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from balance_log where withdrawal_id is not null", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from top_ups where reason='MIGRATION_BALANCE_ALIGNMENT'", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from information_schema.columns where table_name='bot_users' and column_name in ('balance','expire_at','remnawave_uuid')", Integer.class));
        assertNotNull(jdbc.queryForObject("select new_id from migration_id_map where entity='withdrawals' and old_id='123'", java.util.UUID.class));
    }

    @Test
    void rejectsInvalidSubscriptionWithoutPartialRenameOrDataLoss() throws Exception {
        createExistingSchema();
        jdbc.update("insert into bot_users(id,balance) values (10,100)");
        try (Liquibase lb = liquibase(BASELINE)) { lb.changeLogSync(new Contexts(), new LabelExpression()); }
        try (Liquibase lb = liquibase(MASTER)) {
            assertThrows(LiquibaseException.class, () -> lb.update(new Contexts(), new LabelExpression()));
        }
        assertEquals(100L, jdbc.queryForObject("select balance from bot_users where id=10", Long.class));
        assertNull(jdbc.queryForObject("select to_regclass('public.old_bot_users')", String.class));
    }

    @Test
    void rejectsFractionalHistoricalAmountBeforeNumericScaleCanRoundIt() throws Exception {
        createExistingSchema();
        jdbc.execute("""
            INSERT INTO bot_users(id,balance,remnawave_uuid) VALUES(10,100,'uuid');
            INSERT INTO platega_payments(transaction_id,amount,currency,status,payment_method)
                VALUES('precision',100.0001,'RUB','CONFIRMED','SBPQR');
            INSERT INTO top_ups(bot_user_id,value,top_up_source,external_id) VALUES(10,100,'EXTERNAL','precision');
            """);
        try (Liquibase lb = liquibase(BASELINE)) { lb.changeLogSync(new Contexts(), new LabelExpression()); }
        try (Liquibase lb = liquibase(MASTER)) {
            assertThrows(LiquibaseException.class, () -> lb.update(new Contexts(), new LabelExpression()));
        }
        assertNull(jdbc.queryForObject("select to_regclass('public.old_bot_users')", String.class));
        assertEquals(100L, jdbc.queryForObject("select balance from bot_users where id=10", Long.class));
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "migration.dump", matches = ".+")
    void rehearsesMigrationOnSuppliedDumpInIsolatedPostgres() throws Exception {
        var dump = java.nio.file.Path.of(System.getProperty("migration.dump")).toAbsolutePath();
        assertTrue(java.nio.file.Files.isRegularFile(dump), "Dump file must exist");
        POSTGRES.copyFileToContainer(org.testcontainers.utility.MountableFile.forHostPath(dump), "/tmp/migration.dump");
        var restored = POSTGRES.execInContainer("pg_restore", "--clean", "--if-exists", "--no-owner", "--no-privileges",
                "--username", POSTGRES.getUsername(), "--dbname", POSTGRES.getDatabaseName(), "/tmp/migration.dump");
        assertEquals(0, restored.getExitCode(), "Restore must complete without errors");
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new org.springframework.core.io.FileSystemResource("docs/database/preflight.sql"));
        }
        System.out.println("Source payment inconsistencies: " + jdbc.queryForList("""
            select p.currency,p.status,count(*) as records,
                count(*) filter(where p.amount::numeric(20,2)<>t.value) as different_amount,
                count(*) filter(where p.amount::numeric(20,2)<>trunc(p.amount::numeric(20,2))) as fractional_amount
            from top_ups t join platega_payments p on p.transaction_id=t.external_id
            where t.top_up_source='EXTERNAL' and (p.currency<>'RUB' or p.amount::numeric(20,2)<>t.value or p.status<>'CONFIRMED')
            group by p.currency,p.status
            """));
        try (Liquibase lb = liquibase(BASELINE)) { lb.changeLogSync(new Contexts(), new LabelExpression()); }
        try (Liquibase lb = liquibase(MASTER)) { lb.update(new Contexts(), new LabelExpression()); }
        assertEntitiesMatchSchema();
        assertEquals(0, jdbc.queryForObject("""
            select count(*) from (
                select u.id from old_bot_users u join bot_users b on b.telegram_id=u.id
                left join balance_log l on l.account_id=b.account_id
                group by u.id,u.balance having coalesce(sum(l.amount),0)<>u.balance
            ) differences
            """, Integer.class));
        System.out.println("Migration rehearsal: accounts=" + jdbc.queryForObject("select count(*) from accounts", Long.class)
                + ", corrections=" + jdbc.queryForObject("select count(*) from top_ups where reason='MIGRATION_BALANCE_ALIGNMENT'", Long.class)
                + ", manual_payments=" + jdbc.queryForObject("select count(*) from migration_report where reason='UNRESOLVED_OWNER_MANUAL_REVIEW'", Long.class));
    }

    private void createExistingSchema() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/changelog/changes/001-initial-schema.sql"));
        }
    }

    private Liquibase liquibase(String changelog) throws Exception {
        var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
                new JdbcConnection(dataSource.getConnection()));
        return new Liquibase(changelog, new ClassLoaderResourceAccessor(), database);
    }

    private void assertEntitiesMatchSchema() {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("tg.configshop.model", "tg.configshop.external_api.pay");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
        try {
            factory.afterPropertiesSet();
        } finally {
            factory.destroy();
        }
    }
}
