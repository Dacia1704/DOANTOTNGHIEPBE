package com.tamdaptit.rentalmanagement.subscription;

import com.tamdaptit.rentalmanagement.auth.service.impl.JwtTokenServiceImpl;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.sql.*;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Applies the real two-file chain only to a disposable local database. */
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
    private long count(String sql) throws SQLException {
        try(var s=db.createStatement(); var r=s.executeQuery(sql)) { assertTrue(r.next()); return r.getLong(1); }
    }
    private void none(String sql) throws SQLException { assertEquals(0,count(sql),sql); }

    @Test void accountsHaveRequestedCredentialsAndRoles() throws Exception {
        assertEquals(127,count("SELECT COUNT(*) FROM users"));
        for(var role:Map.of("ADMIN",3,"LANDLORD",24,"TENANT",100).entrySet())
            assertEquals(role.getValue().longValue(),count("SELECT COUNT(*) FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE r.code='"+role.getKey()+"'"));
        none("SELECT COUNT(*) FROM users WHERE email NOT LIKE '%@gmail.com' OR status<>'ACTIVE' OR email_verified_at IS NULL OR avatar_url<>'https://img.icons8.com/nolan/1200/user-default.jpg'");
        none("SELECT COUNT(*) FROM users u WHERE NOT EXISTS(SELECT 1 FROM user_roles ur WHERE ur.user_id=u.id)");
        try(var s=db.createStatement(); var r=s.executeQuery("SELECT DISTINCT password FROM users")) {
            int hashes=0; while(r.next()) { hashes++; assertTrue(new BCryptPasswordEncoder(10).matches("123456",r.getString(1))); }
            assertEquals(1,hashes);
        }
    }

    @Test void inventoryImagesAndAddressesAreCoherent() throws Exception {
        for(var entry:Map.of("provinces",34,"wards",3321,"properties",192,"rooms",1368,"listings",192,"room_images",4104).entrySet())
            assertEquals(entry.getValue().longValue(),count("SELECT COUNT(*) FROM "+entry.getKey()));
        assertEquals(72,count("SELECT COUNT(*) FROM listings WHERE status='PUBLISHED'"));
        none("SELECT COUNT(*) FROM properties p JOIN wards w ON w.id=p.ward_id WHERE w.province_id<>p.province_id");
        none("SELECT COUNT(*) FROM rooms r WHERE (SELECT COUNT(*) FROM room_images i WHERE i.room_id=r.id)<>3");
        none("SELECT COUNT(*) FROM room_images WHERE public_id IS NOT NULL OR sort_order NOT BETWEEN 0 AND 2");
        assertEquals(3,count("SELECT COUNT(DISTINCT url) FROM room_images"));
        none("SELECT COUNT(*) FROM properties p WHERE p.rental_mode='WHOLE' AND ((SELECT COUNT(*) FROM rooms r WHERE r.property_id=p.id)<>1 OR EXISTS(SELECT 1 FROM rooms r WHERE r.property_id=p.id AND (r.room_number<>'WHOLE' OR r.floor_number IS NOT NULL)))");
        none("SELECT COUNT(*) FROM rooms r WHERE EXISTS(SELECT 1 FROM room_types child WHERE child.parent_id=r.room_type_id)");
        none("SELECT COUNT(*) FROM listings l WHERE l.status='PUBLISHED' AND (l.expires_at<=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 7 HOUR) OR NOT EXISTS(SELECT 1 FROM rooms r WHERE r.property_id=l.property_id AND r.rental_status='AVAILABLE'))");
        none("SELECT COUNT(*) FROM rental_requests q JOIN rooms r ON r.id=q.room_id JOIN listings l ON l.id=q.listing_id JOIN properties p ON p.id=l.property_id WHERE r.property_id<>p.id OR q.landlord_id<>p.landlord_id");
        none("SELECT COUNT(*) FROM listings l WHERE l.view_count<>(SELECT COUNT(*) FROM listing_view_history h WHERE h.listing_id=l.id) OR l.contact_count<>(SELECT COUNT(*) FROM listing_contact_events h WHERE h.listing_id=l.id)");
    }

    @Test void subscriptionsPaymentsAndBoostAllocationsMatchTheirOwnersAndQuotas() throws Exception {
        assertEquals(7,count("SELECT COUNT(*) FROM service_packages"));
        assertEquals(10,count("SELECT COUNT(*) FROM boost_service_packages"));
        assertEquals(12,count("SELECT COUNT(*) FROM service_package_subscriptions WHERE status='ACTIVE'"));
        assertEquals(12,count("SELECT COUNT(*) FROM boost_service_package_subscriptions WHERE status='ACTIVE'"));
        assertEquals(6,count("SELECT COUNT(DISTINCT s.landlord_id) FROM service_package_subscriptions s JOIN boost_service_package_subscriptions b ON b.landlord_id=s.landlord_id WHERE s.status='ACTIVE' AND b.status='ACTIVE'"));
        assertEquals(132,count("SELECT COUNT(*) FROM payments"));
        assertEquals(66,count("SELECT COUNT(*) FROM invoices"));
        none("SELECT COUNT(*) FROM service_package_subscriptions s WHERE s.status='ACTIVE' AND (SELECT COUNT(*) FROM service_subscription_periods p WHERE p.subscription_id=s.id AND p.start_at<=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 7 HOUR) AND p.end_at>DATE_ADD(UTC_TIMESTAMP(),INTERVAL 7 HOUR))<>1");
        none("SELECT COUNT(*) FROM boost_allocations a JOIN boost_service_package_subscriptions b ON b.id=a.boost_subscription_id JOIN listings l ON l.id=a.listing_id JOIN properties p ON p.id=l.property_id WHERE b.landlord_id<>p.landlord_id OR b.status<>'ACTIVE' OR l.status<>'PUBLISHED'");
        none("SELECT COUNT(*) FROM boost_service_package_subscriptions b WHERE (SELECT COUNT(*) FROM boost_allocations a WHERE a.boost_subscription_id=b.id AND a.priority_level=2)>b.high_priority_slots_snapshot OR (SELECT COUNT(*) FROM boost_allocations a WHERE a.boost_subscription_id=b.id AND a.priority_level=1)>b.low_priority_slots_snapshot");
        none("SELECT COUNT(*) FROM payments p JOIN service_subscription_orders o ON o.id=p.service_subscription_order_id WHERE p.landlord_id<>o.landlord_id OR p.amount<>o.price");
        none("SELECT COUNT(*) FROM payments p JOIN boost_service_package_subscriptions b ON b.id=p.boost_subscription_id WHERE p.landlord_id<>b.landlord_id OR p.amount<>b.price");
        none("SELECT COUNT(*) FROM invoices i JOIN payments p ON p.id=i.payment_id WHERE p.status<>'SUCCESS' OR i.total_amount<>p.amount");
        none("SELECT COUNT(*) FROM listings l JOIN service_subscription_periods s ON s.id=l.service_subscription_period_id JOIN service_package_subscriptions b ON b.id=s.subscription_id JOIN properties p ON p.id=l.property_id WHERE b.landlord_id<>p.landlord_id");
        none("SELECT COUNT(*) FROM (SELECT p.landlord_id FROM listings l JOIN properties p ON p.id=l.property_id WHERE l.status IN('PUBLISHED','PENDING') AND NOT EXISTS(SELECT 1 FROM service_package_subscriptions s WHERE s.landlord_id=p.landlord_id) GROUP BY p.landlord_id HAVING COUNT(*)>3) over_quota");
    }

    @Test void flywayRestartAndCurrentJpaMappingsValidate() throws Exception {
        assertEquals(0,flyway.migrate().migrationsExecuted); flyway.validate();
        assertEquals(2,count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1"));
        none("SELECT COUNT(*) FROM flyway_schema_history WHERE type='BASELINE'");
        var factory=new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(new DriverManagerDataSource(url(schema),user(),password()));
        factory.setPackagesToScan("com.tamdaptit.rentalmanagement");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate"));
        try {
            factory.afterPropertiesSet(); assertNotNull(factory.getObject());
            var em=factory.getObject().createEntityManager();
            try {
                var tokens=new JwtTokenServiceImpl("seed-test-key-only-".repeat(5),"seed-data-test",60000,120000);
                for(String role:new String[]{"admin","landlord","tenant"}) {
                    var account=em.createQuery("SELECT u FROM User u WHERE u.email=:email",User.class).setParameter("email",role+"@gmail.com").getSingleResult();
                    assertTrue(tokens.buildAuthorities(account).contains("ROLE_"+role.toUpperCase()));
                    assertEquals(account.getId(),tokens.getUserId(tokens.generateAccessToken(account)));
                }
            } finally { em.close(); }
        } finally { factory.destroy(); }
    }
}
