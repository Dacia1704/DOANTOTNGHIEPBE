package com.tamdaptit.rentalmanagement.subscription;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Runs against an isolated MySQL server, creating and dropping only its own random schemas. */
@EnabledIfEnvironmentVariable(named = "SUBSCRIPTION_TEST_MYSQL_URL",
        matches = "jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/")
class SubscriptionSchemaMigrationTest {
    private String schema;
    private Connection connection;

    private String user() {
        return System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_USER", "root");
    }

    private String password() {
        return System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_PASSWORD", "");
    }

    private String url(String database) {
        return System.getenv("SUBSCRIPTION_TEST_MYSQL_URL") + database
                + "?serverTimezone=Asia/Ho_Chi_Minh&useSSL=false&allowPublicKeyRetrieval=true";
    }

    @BeforeEach
    void createIsolatedSchema() throws SQLException {
        schema = "subscription_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(url(""), user(), password());
             var statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
        connection = DriverManager.getConnection(url(schema), user(), password());
    }

    @AfterEach
    void dropOnlyTestSchema() throws SQLException {
        if (connection != null) {
            connection.close();
        }
        if (schema != null && schema.matches("subscription_test_[a-f0-9]{32}")) {
            try (Connection admin = DriverManager.getConnection(url(""), user(), password());
                 var statement = admin.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            }
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(url(schema), user(), password())
                .locations("classpath:db/migration").target(target).load();
    }

    private void execute(String sql) throws SQLException {
        if (sql.startsWith("INSERT INTO listings") && sql.contains("room_id")
                && "1".equals(scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='listings' AND column_name='property_id'"))) {
            sql = sql.replace("room_id", "property_id").replace("'room'", "'property'");
        }
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String scalar(String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private void assertConstraintRejects(String sql) {
        SQLException error = assertThrows(SQLException.class, () -> execute(sql));
        assertTrue(Set.of(1062, 1452, 3819).contains(error.getErrorCode()), error.getMessage());
    }

    private void roomFixture() throws SQLException {
        execute("INSERT INTO users(id,email,password) VALUES ('owner','owner@example.test','test-only')");
        execute("INSERT INTO provinces(id,code,name) VALUES ('province','TEST-P','Test')");
        execute("INSERT INTO wards(id,province_id,code,name) VALUES ('ward','province','TEST-W','Test')");
        execute("INSERT INTO room_types(id,name) VALUES ('room-type','Test room')");
        if ("1".equals(scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rooms' AND column_name='property_id'"))) {
            execute("INSERT INTO properties(id,landlord_id,property_type_id,name,rental_mode,province_id,ward_id,address_line) "
                    + "VALUES ('property','owner','00000000-0000-0000-0000-000000000011','Test','BY_ROOM','province','ward','Test address')");
            execute("INSERT INTO rooms(id,property_id,room_number,room_type_id,price,area_m2) VALUES ('room','property','A','room-type',100,20)");
        } else {
            execute("INSERT INTO rooms(id,landlord_id,room_type_id,price,area_m2,province_id,ward_id,address_line) "
                    + "VALUES ('room','owner','room-type',100,20,'province','ward','Test address')");
        }
    }

    @Test
    void propertyMigrationPreservesLegacyRoomIdentityAndAddress() throws SQLException {
        flyway("10").migrate();
        roomFixture();
        execute("UPDATE rooms SET latitude=10.12345678,longitude=106.12345678 WHERE id='room'");
        execute("INSERT INTO listings(id,room_id,title,status) VALUES ('legacy-listing','room','Legacy','HIDDEN')");
        assertEquals(1, flyway("11").migrate().migrationsExecuted);
        assertEquals("room:LEGACY", scalar("SELECT CONCAT(property_id,':',room_number) FROM rooms WHERE id='room'"));
        assertEquals("owner:province:ward:Test address", scalar("SELECT CONCAT(landlord_id,':',province_id,':',ward_id,':',address_line) FROM properties WHERE id='room'"));
        assertEquals("10.12345678:106.12345678", scalar("SELECT CONCAT(latitude,':',longitude) FROM properties WHERE id='room'"));
        assertEquals("room", scalar("SELECT room_id FROM listings WHERE id='legacy-listing'"));
        assertConstraintRejects("INSERT INTO rooms(id,property_id,room_number,room_type_id,price,area_m2) VALUES ('duplicate','room','LEGACY','room-type',100,20)");
        assertEquals("0", scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rooms' AND column_name='landlord_id'"));
    }

    @Test
    void listingPropertyMigrationKeepsPostIdentityAndItsProperty() throws SQLException {
        flyway("12").migrate();
        roomFixture();
        execute("INSERT INTO listings(id,room_id,title,status) VALUES ('preserved-post','room','Old post','HIDDEN')");
        assertEquals(1, flyway("13").migrate().migrationsExecuted);
        assertEquals("property", scalar("SELECT property_id FROM listings WHERE id='preserved-post'"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='listings' AND column_name='room_id'"));
        assertEquals("Old post", scalar("SELECT title FROM listings WHERE id='preserved-post'"));
    }

    private void paidFixture() throws SQLException {
        roomFixture();
        execute("INSERT INTO service_packages(id,code,name,max_listings) VALUES ('package','BASIC','Basic',10)");
        execute("INSERT INTO service_package_prices(id,package_id,duration_months,price) VALUES ('price','package',1,100)");
        execute("INSERT INTO service_package_subscriptions(id,landlord_id,package_id,start_at,end_at) "
                + "VALUES ('subscription','owner','package','2026-10-23','2026-11-23')");
        execute(orderSql("order", "PURCHASE"));
        execute("INSERT INTO service_subscription_periods(id,subscription_id,order_id,period_number,start_at,end_at,"
                + "package_code_snapshot,package_name_snapshot,max_listings_snapshot,high_priority_slots_snapshot,"
                + "low_priority_slots_snapshot,default_priority_level_snapshot,has_stats_snapshot) "
                + "VALUES ('period','subscription','order',1,'2026-10-23','2026-11-23','BASIC','Basic',10,0,5,0,0)");
        execute("INSERT INTO listings(id,room_id,title,status,published_at,expires_at) "
                + "VALUES ('listing','room','Test','PUBLISHED','2026-10-23','2026-11-23')");
        execute("INSERT INTO boost_service_packages(id,code,name,duration_months,price) "
                + "VALUES ('boost','HIGH-1M','High 1 month',1,100)");
    }

    private String orderSql(String id, String type) {
        return "INSERT INTO service_subscription_orders(id,landlord_id,package_id,service_package_price_id,"
                + "type,previous_subscription_id,duration_months,price,package_code_snapshot,package_name_snapshot,"
                + "max_listings_snapshot,high_priority_slots_snapshot,low_priority_slots_snapshot,"
                + "default_priority_level_snapshot,has_stats_snapshot,expires_at) VALUES ('" + id
                + "','owner','package','price','" + type + "',"
                + (type.equals("CHANGE") ? "'subscription'" : "NULL")
                + ",1,100,'BASIC','Basic',10,0,5,0,0,'2026-12-01')";
    }

    private String legacyBoostSql(String id) {
        return "INSERT INTO boost_service_package_subscriptions(id,landlord_id,listing_id,boost_package_id,"
                + "status,start_at,end_at,price,package_name_snapshot,priority_level_snapshot,duration_months_snapshot) "
                + "VALUES ('" + id + "','owner','listing','boost','ACTIVE','2026-10-23','2026-11-23',100,'High',2,1)";
    }

    private String boostSql(String id) {
        return "INSERT INTO boost_service_package_subscriptions(id,landlord_id,boost_package_id,"
                + "status,start_at,end_at,price,package_name_snapshot,high_priority_slots_snapshot,low_priority_slots_snapshot,duration_months_snapshot) "
                + "VALUES ('" + id + "','owner','boost','ACTIVE','2026-10-23','2026-11-23',100,'High',1,0,1)";
    }
    @Test
    void reconciliationMigrationPreservesExistingV3PaymentsAndBoostDates() throws SQLException {
        flyway("3").migrate();
        paidFixture();
        execute(legacyBoostSql("boost-purchase"));
        execute("""
                INSERT INTO payments(id,landlord_id,type,service_subscription_order_id,merchant_order_id,
                expires_at,amount,payment_method,status,paid_at)
                VALUES ('payment','owner','SERVICE_PACKAGE','order','merchant-1','2026-11-01',100,'VNPAY','SUCCESS','2026-10-23')
                """);
        assertEquals(1, flyway("4").migrate().migrationsExecuted);
        assertEquals("SUCCESS", scalar("SELECT status FROM payments WHERE id='payment'"));
        assertEquals("0", scalar("SELECT requires_review FROM payments WHERE id='payment'"));
        assertEquals("2026-11-23 00:00:00", scalar("SELECT CAST(end_at AS CHAR) FROM boost_service_package_subscriptions WHERE id='boost-purchase'"));
    }

    @Test
    void queryDrMigrationBackfillsTheFirstCheckoutDateWithoutChangingPaymentState() throws SQLException {
        flyway("4").migrate();
        paidFixture();
        execute("""
                INSERT INTO payments(id,landlord_id,type,service_subscription_order_id,merchant_order_id,
                expires_at,amount,payment_method,status,created_at)
                VALUES ('payment','owner','SERVICE_PACKAGE','order','merchant-1','2026-11-01',100,'VNPAY','EXPIRED','2026-10-23 10:00:00')
                """);
        assertEquals(1, flyway("5").migrate().migrationsExecuted);
        assertEquals("2026-10-23 10:00:00", scalar("SELECT CAST(vnp_create_date AS CHAR) FROM payments WHERE id='payment'"));
        assertEquals("EXPIRED", scalar("SELECT status FROM payments WHERE id='payment'"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM invoices"));
    }

    @Test
    void slotMigrationPreservesExistingPaidBoostAndItsAssignment() throws SQLException {
        flyway("5").migrate();
        paidFixture();
        execute(legacyBoostSql("old"));
        execute("UPDATE listings SET published_at=CURRENT_TIMESTAMP, expires_at=CURRENT_TIMESTAMP + INTERVAL 1 MONTH WHERE id='listing'");
        execute("UPDATE boost_service_package_subscriptions SET start_at=CURRENT_TIMESTAMP, end_at=CURRENT_TIMESTAMP + INTERVAL 3 MONTH WHERE id='old'");
        String originalEnd = scalar("SELECT CAST(end_at AS CHAR) FROM boost_service_package_subscriptions WHERE id='old'");
        assertEquals(1, flyway("6").migrate().migrationsExecuted);
        assertEquals("1:0:100.00", scalar("SELECT CONCAT(high_priority_slots_snapshot,':',low_priority_slots_snapshot,':',price) FROM boost_service_package_subscriptions WHERE id='old'"));
        assertEquals(originalEnd, scalar("SELECT CAST(end_at AS CHAR) FROM boost_service_package_subscriptions WHERE id='old'"));
        assertEquals("old:listing:2", scalar("SELECT CONCAT(boost_subscription_id,':',listing_id,':',priority_level) FROM boost_allocations"));
        assertConstraintRejects("UPDATE boost_service_packages SET high_priority_slots=0,low_priority_slots=0 WHERE id='boost'");
        assertConstraintRejects("UPDATE boost_allocations SET priority_level=0");
    }

    @Test
    void upgradeMigrationBackfillsPaidValueWithoutChangingExistingOrdersAndDates() throws SQLException {
        flyway("6").migrate();
        paidFixture();
        String previousEnd = scalar("SELECT CAST(end_at AS CHAR) FROM service_package_subscriptions WHERE id='subscription'");
        assertEquals(2, flyway("8").migrate().migrationsExecuted);
        assertEquals("-516256725", scalar("SELECT checksum FROM flyway_schema_history WHERE version='7'"));
        assertEquals("100.00", scalar("SELECT entitlement_value FROM service_subscription_periods WHERE id='period'"));
        assertEquals("1.000000000000", scalar("SELECT billing_months FROM service_subscription_periods WHERE id='period'"));
        assertEquals("0", scalar("SELECT immediate_upgrade FROM service_subscription_orders WHERE id='order'"));
        assertEquals(previousEnd, scalar("SELECT CAST(end_at AS CHAR) FROM service_package_subscriptions WHERE id='subscription'"));
        assertConstraintRejects("UPDATE service_subscription_periods SET entitlement_value=-1 WHERE id='period'");
        assertConstraintRejects("UPDATE service_subscription_orders SET immediate_upgrade=TRUE WHERE id='order'");
    }

    @Test
    void freshSchemaMigratesAndAllJpaMappingsValidate() throws SQLException {
        assertEquals(13, flyway("latest").migrate().migrationsExecuted);
        assertEquals("3", scalar("SELECT max_listings FROM service_packages WHERE code='FREE'"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM service_package_prices"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='listing_priority_assignments'"));

        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(new DriverManagerDataSource(url(schema), user(), password()));
        factory.setPackagesToScan("com.tamdaptit.rentalmanagement");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
        try {
            factory.afterPropertiesSet();
            assertNotNull(factory.getObject());
        } finally {
            factory.destroy();
        }
        assertEquals(0, flyway("latest").migrate().migrationsExecuted);
    }

    @Test
    void billingMigrationPreservesExistingV7UpgradeAndBackfillsPendingQuotes() throws SQLException {
        flyway("7").migrate();
        paidFixture();
        execute("UPDATE service_subscription_periods SET entitlement_value=100 WHERE id='period'");
        execute("UPDATE service_package_subscriptions SET status='CANCELLED' WHERE id='subscription'");
        execute("INSERT INTO service_package_subscriptions(id,landlord_id,package_id,start_at,end_at) "
                + "VALUES ('up','owner','package','2026-11-07','2026-11-23')");
        execute("""
                INSERT INTO service_subscription_orders(id,landlord_id,package_id,service_package_price_id,
                subscription_id,previous_subscription_id,type,status,duration_months,price,
                package_code_snapshot,package_name_snapshot,max_listings_snapshot,high_priority_slots_snapshot,
                low_priority_slots_snapshot,default_priority_level_snapshot,has_stats_snapshot,expires_at,
                effective_at,end_at,previous_end_at,applied_at,created_at,immediate_upgrade,upgrade_value)
                VALUES ('up-order','owner','package','price','up','subscription','CHANGE','APPLIED',1,50,
                'BASIC','Basic',20,0,5,0,0,'2026-11-07 00:15:00',
                '2026-11-07','2026-11-23','2026-11-23','2026-11-07','2026-11-07',TRUE,103.23)
                """);
        execute("""
                INSERT INTO service_subscription_periods(id,subscription_id,order_id,period_number,start_at,end_at,
                package_code_snapshot,package_name_snapshot,max_listings_snapshot,high_priority_slots_snapshot,
                low_priority_slots_snapshot,default_priority_level_snapshot,has_stats_snapshot,entitlement_value)
                VALUES ('up-period','up','up-order',1,'2026-11-07','2026-11-23','BASIC','Basic',20,0,5,0,0,103.23)
                """);
        execute("""
                INSERT INTO service_subscription_orders(id,landlord_id,package_id,service_package_price_id,
                previous_subscription_id,type,duration_months,price,package_code_snapshot,package_name_snapshot,
                max_listings_snapshot,high_priority_slots_snapshot,low_priority_slots_snapshot,
                default_priority_level_snapshot,has_stats_snapshot,expires_at,previous_end_at,created_at,
                immediate_upgrade,upgrade_value)
                VALUES ('pending-up','owner','package','price','up','CHANGE',1,30,'BASIC','Basic',30,0,5,0,0,
                '2026-11-15 00:15:00','2026-11-23','2026-11-15',TRUE,80)
                """);
        assertEquals(1, flyway("8").migrate().migrationsExecuted);
        assertEquals("-516256725", scalar("SELECT checksum FROM flyway_schema_history WHERE version='7'"));
        assertEquals("0.516129032258", scalar("SELECT billing_months FROM service_subscription_periods WHERE id='up-period'"));
        assertEquals("0.258064516129", scalar("SELECT upgrade_months FROM service_subscription_orders WHERE id='pending-up'"));
        assertEquals("103.23", scalar("SELECT entitlement_value FROM service_subscription_periods WHERE id='up-period'"));
        assertEquals("50.00:APPLIED", scalar("SELECT CONCAT(price,':',status) FROM service_subscription_orders WHERE id='up-order'"));
    }

    @Test
    void upgradesExistingListingsAndKeepsUnconvertedLegacyCatalogueData() throws SQLException {
        flyway("2").migrate();
        roomFixture();
        execute("INSERT INTO service_packages(id,code,name,price,billing_cycle_days) "
                + "VALUES ('old-package','OLD','Legacy',123,45)");
        execute("INSERT INTO boost_service_packages(id,code,name,price,duration_days,priority_level) "
                + "VALUES ('old-boost','OLD-BOOST','Legacy Boost',25,7,1)");
        execute("INSERT INTO listings(id,room_id,title,status,approved_at,expires_at) "
                + "VALUES ('published','room','Published','APPROVED','2026-01-31 10:00:00','2026-02-28 10:00:00')");
        execute("INSERT INTO listings(id,room_id,title,created_at) "
                + "VALUES ('pending','room','Pending','2026-01-31 10:00:00')");

        flyway("latest").migrate();
        assertEquals("PUBLISHED", scalar("SELECT status FROM listings WHERE id='published'"));
        assertEquals("2026-01-31 10:00:00", scalar("SELECT CAST(published_at AS CHAR) FROM listings WHERE id='published'"));
        assertEquals("2026-02-28 10:00:00", scalar("SELECT CAST(expires_at AS CHAR) FROM listings WHERE id='published'"));
        assertEquals("2026-02-07 10:00:00", scalar("SELECT CAST(pending_expires_at AS CHAR) FROM listings WHERE id='pending'"));
        assertEquals("123.00:45", scalar("SELECT CONCAT(legacy_price,':',legacy_billing_cycle_days) FROM service_packages WHERE id='old-package'"));
        assertEquals("7:1:0", scalar("SELECT CONCAT(legacy_duration_days,':',legacy_priority_level,':',is_active) FROM boost_service_packages WHERE id='old-boost'"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM service_package_prices"));
        assertNull(scalar("SELECT duration_months FROM boost_service_packages WHERE id='old-boost'"));
    }

    @Test
    void enforcesActiveScheduledOpenChangeAndBoostUniqueness() throws SQLException {
        flyway("latest").migrate();
        paidFixture();
        assertConstraintRejects("INSERT INTO service_package_subscriptions(id,landlord_id,package_id,start_at,end_at) "
                + "VALUES ('duplicate','owner','package','2026-10-23','2026-11-23')");
        execute("INSERT INTO service_package_subscriptions(id,landlord_id,package_id,start_at,end_at,status) "
                + "VALUES ('scheduled','owner','package','2026-11-23','2026-12-23','SCHEDULED')");
        assertConstraintRejects("INSERT INTO service_package_subscriptions(id,landlord_id,package_id,start_at,end_at,status) "
                + "VALUES ('scheduled-2','owner','package','2026-11-23','2026-12-23','SCHEDULED')");
        execute(orderSql("change-1", "CHANGE"));
        assertConstraintRejects(orderSql("change-2", "CHANGE"));
        execute("UPDATE service_subscription_orders SET status='CANCELLED' WHERE id='change-1'");
        execute(orderSql("change-2", "CHANGE"));

        execute(boostSql("boost-1"));
        execute(boostSql("boost-2"));
        execute("INSERT INTO boost_allocations(id,boost_subscription_id,listing_id,priority_level) VALUES ('a','boost-1','listing',2)");
        assertConstraintRejects("INSERT INTO boost_allocations(id,boost_subscription_id,listing_id,priority_level) VALUES ('b','boost-2','listing',1)");
        execute("DELETE FROM boost_allocations WHERE id='a'");
        execute("INSERT INTO boost_allocations(id,boost_subscription_id,listing_id,priority_level) VALUES ('b','boost-2','listing',1)");
    }

    @Test
    void storesPackagePriorityOnListingAndRequiresMatchingPeriodReference() throws SQLException {
        flyway("latest").migrate();
        paidFixture();
        assertEquals("0", scalar("SELECT package_priority_level FROM listings WHERE id='listing'"));
        assertNull(scalar("SELECT service_subscription_period_id FROM listings WHERE id='listing'"));
        assertConstraintRejects("UPDATE listings SET package_priority_level=1 WHERE id='listing'");
        assertConstraintRejects("UPDATE listings SET service_subscription_period_id='period' WHERE id='listing'");
        assertConstraintRejects("UPDATE listings SET service_subscription_period_id='missing',package_priority_level=1 WHERE id='listing'");
        execute("UPDATE listings SET service_subscription_period_id='period',package_priority_level=1 WHERE id='listing'");
        execute("UPDATE listings SET package_priority_level=2 WHERE id='listing'");
        assertEquals("period:2", scalar("SELECT CONCAT(service_subscription_period_id,':',package_priority_level) FROM listings WHERE id='listing'"));
        assertConstraintRejects("UPDATE listings SET package_priority_level=3 WHERE id='listing'");
        assertConstraintRejects("UPDATE listings SET service_subscription_period_id=NULL WHERE id='listing'");
        execute("UPDATE listings SET service_subscription_period_id=NULL,package_priority_level=0 WHERE id='listing'");
        assertEquals("0", scalar("SELECT package_priority_level FROM listings WHERE id='listing'"));
        assertNull(scalar("SELECT service_subscription_period_id FROM listings WHERE id='listing'"));
    }

    @Test
    void rejectsInvalidTermsDatesPaymentTargetsAndDuplicateProviderTransactions() throws SQLException {
        flyway("latest").migrate();
        paidFixture();
        assertConstraintRejects("INSERT INTO service_package_prices(package_id,duration_months,price) VALUES ('package',2,100)");
        assertConstraintRejects("INSERT INTO boost_service_packages(code,name,duration_months,price) VALUES ('BAD','Bad',12,100)");
        assertConstraintRejects("UPDATE service_subscription_periods SET end_at=start_at WHERE id='period'");
        assertConstraintRejects("UPDATE service_subscription_orders SET high_priority_slots_snapshot=-1 WHERE id='order'");
        assertConstraintRejects("INSERT INTO listings(room_id,title,status) VALUES ('room','No deadline','PENDING')");
        assertConstraintRejects("INSERT INTO listings(room_id,title,status) VALUES ('room','No publication','PUBLISHED')");
        execute(boostSql("boost-1"));
        String payment = "INSERT INTO payments(id,landlord_id,type,service_subscription_order_id,boost_subscription_id,"
                + "merchant_order_id,amount,payment_method,expires_at,provider_txn_id) VALUES ";
        assertConstraintRejects(payment + "('bad','owner','SERVICE_PACKAGE','order','boost-1','bad',100,'VNPAY','2026-12-01',NULL)");
        assertConstraintRejects(payment + "('bad','owner','SERVICE_PACKAGE',NULL,NULL,'bad',100,'VNPAY','2026-12-01',NULL)");
        assertConstraintRejects(payment + "('bad','owner','BOOST_PACKAGE','order',NULL,'bad',100,'VNPAY','2026-12-01',NULL)");
        assertConstraintRejects(payment + "('bad','owner','SERVICE_PACKAGE','missing',NULL,'bad',100,'VNPAY','2026-12-01',NULL)");
        execute(payment + "('payment-1','owner','SERVICE_PACKAGE','order',NULL,'merchant-1',100,'VNPAY','2026-12-01','provider-1')");
        assertConstraintRejects(payment + "('payment-2','owner','SERVICE_PACKAGE','order',NULL,'merchant-2',100,'VNPAY','2026-12-01','provider-1')");
        assertConstraintRejects(payment + "('payment-2','owner','SERVICE_PACKAGE','order',NULL,'merchant-1',100,'VNPAY','2026-12-01',NULL)");
        assertConstraintRejects("UPDATE payments SET status='SUCCESS' WHERE id='payment-1'");
    }

    @Test
    void ambiguousLegacyFinancialHistoryStopsBeforePersistentSchemaChanges() throws SQLException {
        flyway("2").migrate();
        roomFixture();
        execute("INSERT INTO service_packages(id,code,name,price) VALUES ('legacy','LEGACY','Legacy',100)");
        execute("INSERT INTO service_package_subscriptions(id,landlord_id,package_id,start_at,end_at) "
                + "VALUES ('old','owner','legacy','2026-10-23','2026-11-23')");
        FlywayException error = assertThrows(FlywayException.class, () -> flyway("latest").migrate());
        assertTrue(error.getMessage().contains("v3_requires_legacy_financial_reconciliation"));
        assertEquals("1", scalar("SELECT active_flag FROM service_package_subscriptions WHERE id='old'"));
        assertEquals("100.00", scalar("SELECT price FROM service_packages WHERE id='legacy'"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='service_package_prices'"));
    }
}
