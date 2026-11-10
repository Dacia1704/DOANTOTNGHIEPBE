package com.tamdaptit.rentalmanagement.identity;

import com.tamdaptit.rentalmanagement.address.repository.NearbyPlaceRepository;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.identity.dto.request.TenantPreferences;
import com.tamdaptit.rentalmanagement.identity.repository.*;
import com.tamdaptit.rentalmanagement.identity.service.TenantPreferencesService;
import com.tamdaptit.rentalmanagement.identity.service.impl.TenantPreferencesServiceImpl;
import jakarta.persistence.*;
import jakarta.validation.Validation;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual MySQL migration + JPA persistence; never touches the application's schema. */
@EnabledIfEnvironmentVariable(named = "SUBSCRIPTION_TEST_MYSQL_URL", matches = "jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/")
class TenantPreferencesIntegrationTest {
    static final String schema = "preferences_test_" + UUID.randomUUID().toString().replace("-", "");
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static TenantPreferencesService service;
    static String user() { return System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_USER", "root"); }
    static String password() { return System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_PASSWORD", ""); }
    static String url() { return System.getenv("SUBSCRIPTION_TEST_MYSQL_URL") + schema + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh"; }

    @BeforeAll static void start() throws Exception {
        try (var db = DriverManager.getConnection(System.getenv("SUBSCRIPTION_TEST_MYSQL_URL") + "?useSSL=false&allowPublicKeyRetrieval=true", user(), password()); var s = db.createStatement()) {
            s.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
        Flyway.configure().dataSource(url(), user(), password()).target("1").load().migrate();
        try (var db = DriverManager.getConnection(url(), user(), password())) {
            ScriptUtils.executeSqlScript(db, new ClassPathResource("db/migration/V6__notification_delivery.sql"));
            ScriptUtils.executeSqlScript(db, new ClassPathResource("db/migration/V7__role_permission_management.sql"));
            ScriptUtils.executeSqlScript(db, new ClassPathResource("db/migration/V8__nearby_places.sql"));
        }
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = context.getBean(JdbcTemplate.class); service = context.getBean(TenantPreferencesService.class);
        jdbc.update("INSERT INTO users(id,email,password) VALUES('tenant','tenant@preferences.test','unused'),('other','other@preferences.test','unused')");
        jdbc.update("INSERT INTO provinces(id,code,name) VALUES('p','P','Province'),('p2','P2','Other province')");
        jdbc.update("INSERT INTO wards(id,code,name,province_id) VALUES('w','W','Ward','p'),('w2','W2','Other ward','p2')");
        jdbc.update("INSERT INTO amenities(id,name) VALUES('wifi','Wifi')");
    }
    @AfterAll static void stop() throws Exception {
        if (context != null) context.close();
        if (schema.matches("preferences_test_[a-f0-9]{32}")) {
            try (var db = DriverManager.getConnection(url(), user(), password()); var s = db.createStatement()) { s.execute("DROP DATABASE `" + schema + "`"); }
        }
    }
    static TenantPreferences request(String place, String label, BigDecimal lat, BigDecimal lng, List<String> wards) {
        return new TenantPreferences(null, 2, null, "p", wards, null,
                new BigDecimal("2000000"), new BigDecimal("5000000"), null, null,
                place, label, lat, lng, new BigDecimal("3.5"), 20, null, "Private note", true,
                List.of("wifi"), List.of("wifi"));
    }
    @Test void savesCatalogueOrCustomCoordinatesAndReadsExactlyWhatAiUses() {
        assertNull(service.get("other"));
        var saved = service.save("tenant", request("poi-hn-hust", "Forged label", BigDecimal.ZERO, BigDecimal.ZERO, List.of("w")));
        assertEquals("poi-hn-hust", saved.anchorPlaceId());
        assertEquals("Đại học Bách khoa Hà Nội", saved.anchorLabel());
        assertEquals(0, new BigDecimal("21.007").compareTo(saved.anchorLatitude()));
        assertEquals(List.of("wifi"), service.get("tenant").requiredAmenityIds());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tenant_profile_wards", Integer.class));
        var custom = service.save("tenant", request(null, null, new BigDecimal("10.12345678"), new BigDecimal("106.12345678"), List.of()));
        assertNull(custom.anchorPlaceId()); assertNull(custom.anchorLabel());
        assertEquals(0, new BigDecimal("10.12345678").compareTo(jdbc.queryForObject("SELECT anchor_latitude FROM tenant_profiles WHERE user_id='tenant'", BigDecimal.class)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tenant_profile_wards", Integer.class));
        assertNull(service.get("other"));
        var cleared = service.save("tenant", request(null, null, null, null, List.of()));
        assertNull(cleared.anchorLatitude()); assertNull(cleared.anchorLongitude());
    }
    @Test void rejectsInvalidReferencesMismatchedCoordinatesAndForeignProvinceWards() {
        assertThrows(AppException.class, () -> service.save("other", request("missing", null, null, null, List.of())));
        assertThrows(AppException.class, () -> service.save("other", request(null, null, BigDecimal.ONE, null, List.of())));
        assertThrows(AppException.class, () -> service.save("other", request(null, null, null, null, List.of("w2"))));
        assertNull(service.get("other"));
    }
    @Test void validatesCoordinateBoundsAndSeedCatalogue() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertFalse(factory.getValidator().validate(request(null, null, new BigDecimal("91"), BigDecimal.ONE, List.of())).isEmpty());
        }
        var places = context.getBean(NearbyPlaceRepository.class);
        assertEquals(13, places.count());
        assertFalse(places.search("Bách khoa", "UNIVERSITY", org.springframework.data.domain.PageRequest.of(0, 100)).isEmpty());
    }
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = { TenantProfileRepository.class, NearbyPlaceRepository.class })
    @Import(TenantPreferencesServiceImpl.class)
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource(url(), user(), password()); }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var f = new LocalContainerEntityManagerFactoryBean(); f.setDataSource(ds);
            f.setPackagesToScan("com.tamdaptit.rentalmanagement"); f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate")); return f;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory f) { return new JpaTransactionManager(f); }
    }
}
