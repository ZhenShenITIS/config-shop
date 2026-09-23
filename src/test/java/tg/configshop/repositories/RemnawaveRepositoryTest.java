package tg.configshop.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.model.BotUser;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(RemnawaveRepositoryTest.Config.class)
@Transactional
class RemnawaveRepositoryTest {
    @Autowired
    private BotUserRepository repository;
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void updatesOnlyRemoteIdAndSupportsBothLookups() {
        Instant expiry = Instant.parse("2026-10-01T00:00:00Z");
        repository.saveAndFlush(BotUser.builder().id(736L).remnawaveUuid("uuid").balance(500L)
                .shortId("short").username("handle").expireAt(expiry).build());
        assertEquals(1, repository.countByRemnawaveIdIsNull());
        assertEquals(736L, repository.findAllByRemnawaveIdIsNullOrderByIdAsc().getFirst().getId());
        assertEquals(1, repository.updateRemnawaveId(736L, 9876543210L));
        entityManager.clear();

        BotUser user = repository.findByRemnawaveId(9876543210L).orElseThrow();
        assertEquals(736L, user.getId());
        assertEquals("uuid", user.getRemnawaveUuid());
        assertEquals(500L, user.getBalance());
        assertEquals("short", user.getShortId());
        assertEquals("handle", user.getUsername());
        assertEquals(expiry, user.getExpireAt());
        assertEquals(0, repository.countByRemnawaveIdIsNull());
        assertTrue(repository.findAllByRemnawaveIdIsNullOrderByIdAsc().isEmpty());
        assertEquals(736L, repository.findByRemnawaveIdWithLock(9876543210L).orElseThrow().getId());
        assertEquals(736L, repository.findByRemnawaveUuidWithLock("uuid").orElseThrow().getId());
    }

    @Test
    void v3UserCanBeInsertedWithoutUuid() {
        repository.saveAndFlush(BotUser.builder().id(737L).remnawaveId(123L).build());
        entityManager.clear();
        assertNull(repository.findByRemnawaveId(123L).orElseThrow().getRemnawaveUuid());
    }

    @Configuration
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
            factory.setPackagesToScan("tg.configshop.model");
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
