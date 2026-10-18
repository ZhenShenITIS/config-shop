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

        assertEquals(21, jdbc.queryForObject("""
                SELECT count(*) FROM pg_tables WHERE schemaname = 'public'
                AND tablename NOT IN ('databasechangelog', 'databasechangeloglock')
                """, Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Integer.class));
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

        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Integer.class));
        assertEquals("db/changelog/changes/001-initial-schema.sql",
                jdbc.queryForObject("SELECT filename FROM databasechangelog", String.class));
        assertEquals(100L, jdbc.queryForObject("SELECT amount FROM promo_codes", Long.class));
        assertEquals(2L, jdbc.queryForObject(
                "INSERT INTO promo_codes (code) VALUES ('AFTER_SYNC') RETURNING id", Long.class));
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
