package tg.configshop.repositories;

import tg.configshop.model.RemnawaveSubscription;
import tg.configshop.model.Account;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(RemnawaveRepositoryTest.Config.class)
@Transactional
class RemnawaveRepositoryTest {
    @Autowired
    private RemnawaveSubscriptionRepository repository;
    @Autowired private AccountRepository accounts;
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void updatesOnlyRemoteIdAndSupportsBothLookups() {
        var account = accounts.save(Account.builder().build());
        var sub = repository.saveAndFlush(RemnawaveSubscription.builder()
                .account(account).remnawaveUuid("uuid").shortId("short").build());
        var id = sub.getId();
        assertEquals(1, repository.countByRemnawaveIdIsNull());
        assertEquals(1, repository.updateRemnawaveId(id, 9876543210L));
        entityManager.clear();
        var loaded = repository.findByRemnawaveIdWithLock(9876543210L).orElseThrow();
        assertEquals(id, loaded.getId());
        assertEquals("uuid", loaded.getRemnawaveUuid());
        assertEquals("short", loaded.getShortId());
        assertEquals(account.getId(), loaded.getAccount().getId());
        assertEquals(id, repository.findByRemnawaveUuidWithLock("uuid").orElseThrow().getId());
        assertEquals(0, repository.countByRemnawaveIdIsNull());
    }
    @Test
    void v3SubscriptionDoesNotRequireUuid() {
        var account = accounts.save(Account.builder().build());
        repository.saveAndFlush(RemnawaveSubscription.builder().account(account).remnawaveId(123L).build());
        entityManager.clear();
        assertNull(repository.findByRemnawaveIdWithLock(123L).orElseThrow().getRemnawaveUuid());
    }

    @org.springframework.boot.test.context.TestConfiguration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = BotUserRepository.class)
    static class Config {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:remnawave-test;MODE=PostgreSQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("tg.configshop.model", "tg.configshop.external_api.pay.model");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of(
                    "hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.hbm2ddl.halt_on_error", "true",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
            return factory;
        }

        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }
}
