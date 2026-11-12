package com.tamdaptit.rentalmanagement.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.file.Path;
import java.sql.*;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="SUBSCRIPTION_TEST_MYSQL_URL", matches="jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/")
class SeedDataMigrationTest {
    private final String schema = "seed_test_" + UUID.randomUUID().toString().replace("-", "");
    private Connection db;
    private Flyway flyway;
    private String user() { return System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_USER", "root"); }
    private String password() { return System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_PASSWORD", ""); }
    private String url(String name) { return System.getenv("SUBSCRIPTION_TEST_MYSQL_URL") + name + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh"; }

    @BeforeAll void migrate() throws Exception {
        try (var admin=DriverManager.getConnection(url(""),user(),password()); var s=admin.createStatement()) {
            s.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        flyway=Flyway.configure().dataSource(url(schema),user(),password()).baselineOnMigrate(false).load();
        assertEquals(2,flyway.migrate().migrationsExecuted);
        db=DriverManager.getConnection(url(schema),user(),password());
    }
    @AfterAll void cleanup() throws Exception {
        if(db!=null) db.close();
        if(schema.matches("seed_test_[a-f0-9]{32}")) {
            try(var admin=DriverManager.getConnection(url(""),user(),password()); var s=admin.createStatement()) {
                s.execute("DROP DATABASE IF EXISTS `"+schema+"`");
            }
        }
    }
    private long count(String sql) throws Exception {
        try(var s=db.createStatement();var r=s.executeQuery(sql)){assertTrue(r.next());return r.getLong(1);}
    }
    private void none(String sql) throws Exception { assertEquals(0,count(sql),sql); }

    @Test void everyApplicationTableIsPopulatedAndCountsMatchGenerator() throws Exception {
        try(var query=db.createStatement();var rows=query.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name<>'flyway_schema_history'")) {
            int tables=0;
            while(rows.next()) { assertTrue(count("SELECT COUNT(*) FROM `"+rows.getString(1)+"`")>0, rows.getString(1)); tables++; }
            assertEquals(49,tables);
        }
        var counts=new ObjectMapper().readTree(Path.of("scripts/data/demo-counts.json").toFile());
        var fields=counts.fields();
        while(fields.hasNext()) {var entry=fields.next();assertEquals(entry.getValue().asLong(),count("SELECT COUNT(*) FROM `"+entry.getKey()+"`"),entry.getKey());}
        none("SELECT COUNT(*) FROM refresh_tokens WHERE revoked=0 OR expires_at>NOW()");
    }

    @Test void accountsRolesImagesAndAddressCoordinatesAreUsable() throws Exception {
        try(var s=db.createStatement();var r=s.executeQuery("SELECT password FROM users WHERE email='tenant@gmail.com'")) {
            assertTrue(r.next());assertTrue(new BCryptPasswordEncoder().matches("123456",r.getString(1)));
        }
        none("SELECT COUNT(*) FROM users u WHERE u.status<>'ACTIVE' OR u.email_verified_at IS NULL OR NOT EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=u.id)");
        none("SELECT COUNT(*) FROM properties p JOIN wards w ON w.id=p.ward_id WHERE p.province_id<>w.province_id OR ST_Distance_Sphere(POINT(p.longitude,p.latitude),POINT(w.centroid_lng,w.centroid_lat))>2000");
        none("SELECT COUNT(*) FROM provinces WHERE centroid_lat IS NULL OR centroid_lng IS NULL");
        none("SELECT COUNT(*) FROM wards WHERE centroid_lat IS NULL OR centroid_lng IS NULL");
        none("SELECT COUNT(*) FROM rooms r WHERE (SELECT COUNT(*) FROM room_images i WHERE i.room_id=r.id)<>3");
        none("SELECT COUNT(*) FROM properties p WHERE (SELECT COUNT(*) FROM property_images i WHERE i.property_id=p.id)<>3");
        assertTrue(count("SELECT COUNT(*) FROM listings l JOIN properties p ON p.id=l.property_id JOIN wards w ON w.id=p.ward_id JOIN rooms r ON r.property_id=p.id WHERE w.code='00166' AND l.status='PUBLISHED' AND l.expires_at>NOW() AND r.rental_status='AVAILABLE' AND r.price<=3000000")>0);
    }

    @Test void reviewsBelongToCompletedRentalsAndReportsRetainEvidence() throws Exception {
        for(String table: new String[]{"room_reviews","landlord_reviews","tenant_reviews"}) {
            none("SELECT COUNT(*) FROM "+table+" r JOIN rental_agreements a ON a.id=r.rental_agreement_id WHERE a.status NOT IN('EXPIRED','TERMINATED') OR a.tenant_id<>r.tenant_id OR r.rating NOT BETWEEN 1 AND 5");
            assertTrue(count("SELECT COUNT(*) FROM "+table+" WHERE is_hidden=1 AND hidden_by IS NOT NULL")>0);
        }
        none("SELECT COUNT(*) FROM room_reviews r JOIN rental_agreements a ON a.id=r.rental_agreement_id WHERE r.room_id<>a.room_id");
        none("SELECT COUNT(*) FROM landlord_reviews r JOIN rental_agreements a ON a.id=r.rental_agreement_id WHERE r.landlord_id<>a.landlord_id");
        none("SELECT COUNT(*) FROM reports r WHERE r.listing_id IS NULL AND r.landlord_id IS NULL");
        none("SELECT COUNT(*) FROM reports r WHERE NOT EXISTS(SELECT 1 FROM report_evidences e WHERE e.report_id=r.id)");
        none("SELECT COUNT(*) FROM rental_events WHERE delivered_at IS NULL");
        none("SELECT COUNT(*) FROM rental_agreements WHERE pdf_status='READY'");
    }

    @Test void historiesBillingAndOccupancyStayConsistent() throws Exception {
        none("SELECT COUNT(*) FROM listings l WHERE l.view_count<>(SELECT COUNT(*) FROM listing_view_history h WHERE h.listing_id=l.id) OR l.contact_count<>(SELECT COUNT(*) FROM listing_contact_events h WHERE h.listing_id=l.id)");
        none("SELECT COUNT(*) FROM payments p JOIN service_subscription_orders o ON o.id=p.service_subscription_order_id WHERE p.landlord_id<>o.landlord_id OR p.amount<>o.price");
        none("SELECT COUNT(*) FROM payments p JOIN boost_service_package_subscriptions b ON b.id=p.boost_subscription_id WHERE p.landlord_id<>b.landlord_id OR p.amount<>b.price");
        none("SELECT COUNT(*) FROM invoices i JOIN payments p ON p.id=i.payment_id WHERE p.status<>'SUCCESS' OR i.total_amount<>p.amount");
        none("SELECT COUNT(*) FROM rental_agreements a JOIN rooms r ON r.id=a.room_id WHERE a.status='ACTIVE' AND r.rental_status<>'RENTED'");
    }

    @Test void flywayIsRepeatableAndCurrentJpaMappingsValidate() throws Exception {
        assertEquals(0,flyway.migrate().migrationsExecuted);flyway.validate();
        assertEquals(2,count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1"));
        var factory=new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(new DriverManagerDataSource(url(schema),user(),password()));
        factory.setPackagesToScan("com.tamdaptit.rentalmanagement");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.dialect","org.hibernate.dialect.MySQLDialect"));
        factory.afterPropertiesSet();factory.destroy();
    }
}
