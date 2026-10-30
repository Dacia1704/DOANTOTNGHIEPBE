package com.tamdaptit.rentalmanagement.subscription;

import com.tamdaptit.rentalmanagement.auth.service.impl.JwtTokenServiceImpl;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Applies the actual SQL files only to disposable schemas on a dedicated local MySQL server. */
@EnabledIfEnvironmentVariable(named = "SUBSCRIPTION_TEST_MYSQL_URL",
        matches = "jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/")
class SeedDataMigrationTest {
    private static final String MIGRATION_DIR = "src/main/resources/db/migration/";
    private static final String SEED = MIGRATION_DIR + "V2__seed_reference_and_demo_data.sql";
    private static final String DEMO_PASSWORD = "TroTotDemo@2026";
    private static final List<String> DEMO_EMAILS = List.of(
            "admin.demo@trotot.example.test", "landlord.hanoi@trotot.example.test",
            "landlord.south@trotot.example.test", "tenant.an@trotot.example.test",
            "tenant.binh@trotot.example.test");
    private static final Set<String> PERMISSIONS = Set.of(
            "USER_READ", "USER_MANAGE", "ROOM_READ", "ROOM_CREATE", "ROOM_UPDATE", "ROOM_DELETE",
            "LISTING_READ", "LISTING_CREATE", "LISTING_MANAGE", "WARD_WRITE", "WARD_DELETE");
    private static final List<String> SEEDED_TABLES = List.of(
            "users", "roles", "permissions", "user_roles", "role_permissions", "provinces", "wards",
            "property_types", "properties", "room_types", "rooms", "amenities", "property_amenities", "room_amenities");

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
    void createAndSeedIsolatedSchema() throws SQLException {
        schema = "seed_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(url(""), user(), password());
             var statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
        connection = DriverManager.getConnection(url(schema), user(), password());
        ScriptUtils.executeSqlScript(connection, new FileSystemResource(MIGRATION_DIR + "V1__fullsql.sql"));
        applySeed();
    }

    @AfterEach
    void dropOnlyThisTestSchema() throws SQLException {
        if (connection != null) {
            connection.close();
        }
        if (schema != null && schema.matches("seed_test_[a-f0-9]{32}")) {
            try (Connection admin = DriverManager.getConnection(url(""), user(), password());
                 var statement = admin.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            }
        }
    }

    private void applySeed() {
        ScriptUtils.executeSqlScript(connection, new FileSystemResource(SEED));
    }

    private String scalar(String sql) throws SQLException {
        return scalar(connection, sql);
    }

    private String scalar(Connection source, String sql) throws SQLException {
        try (var statement = source.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next(), sql);
            return result.getString(1);
        }
    }

    private Set<String> values(String sql) throws SQLException {
        Set<String> values = new TreeSet<>();
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            while (result.next()) {
                values.add(result.getString(1));
            }
        }
        return values;
    }

    private Map<String, String> rowCounts() throws SQLException {
        return rowCounts(connection);
    }

    private Map<String, String> rowCounts(Connection source) throws SQLException {
        Map<String, String> counts = new LinkedHashMap<>();
        for (String table : SEEDED_TABLES) {
            counts.put(table, scalar(source, "SELECT COUNT(*) FROM " + table));
        }
        return counts;
    }

    @Test
    void seedUsesOfficialAddressCodesAndCoherentPropertyRoomRelationships() throws SQLException {
        assertEquals("5", scalar("SELECT COUNT(*) FROM users"));
        assertEquals("3", scalar("SELECT COUNT(*) FROM roles"));
        assertEquals(PERMISSIONS, values("SELECT code FROM permissions"));
        assertEquals(new TreeSet<>(DEMO_EMAILS), values("SELECT email FROM users"));
        assertEquals("34", scalar("SELECT COUNT(*) FROM provinces"));
        assertEquals("3321", scalar("SELECT COUNT(*) FROM wards"));
        assertEquals("2599", scalar("SELECT COUNT(*) FROM wards WHERE name LIKE 'Xã %'"));
        assertEquals("709", scalar("SELECT COUNT(*) FROM wards WHERE name LIKE 'Phường %'"));
        assertEquals("13", scalar("SELECT COUNT(*) FROM wards WHERE name LIKE 'Đặc khu %'"));
        assertEquals("Thành phố Quảng Ninh", scalar("SELECT name FROM provinces WHERE code='22'"));
        assertEquals("Thành phố Bắc Ninh", scalar("SELECT name FROM provinces WHERE code='24'"));
        assertEquals("Thành phố Đồng Nai", scalar("SELECT name FROM provinces WHERE code='75'"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM provinces WHERE code NOT REGEXP '^[0-9]{2}$'"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM wards WHERE code NOT REGEXP '^[0-9]{5}$'"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM wards w LEFT JOIN provinces p ON p.id=w.province_id WHERE p.id IS NULL"));
        assertEquals("4", scalar("SELECT COUNT(*) FROM property_types"));
        assertEquals("12", scalar("SELECT COUNT(*) FROM room_types"));
        assertEquals("14", scalar("SELECT COUNT(*) FROM amenities"));
        assertEquals("6", scalar("SELECT COUNT(*) FROM properties"));
        assertEquals("14", scalar("SELECT COUNT(*) FROM rooms"));
        assertEquals("4", scalar("SELECT COUNT(*) FROM properties WHERE rental_mode='BY_ROOM'"));
        assertEquals("2", scalar("SELECT COUNT(*) FROM properties WHERE rental_mode='WHOLE'"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM listings"), "Seed assets must not publish listings");
        assertEquals("0", scalar("SELECT COUNT(*) FROM properties p JOIN wards w ON w.id=p.ward_id WHERE p.province_id<>w.province_id"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM properties p WHERE NOT EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=p.landlord_id AND r.code='LANDLORD')"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM properties p WHERE p.rental_mode='WHOLE' AND ((SELECT COUNT(*) FROM rooms r WHERE r.property_id=p.id)<>1 OR EXISTS (SELECT 1 FROM rooms r WHERE r.property_id=p.id AND (r.room_number<>'WHOLE' OR r.floor_number IS NOT NULL)))"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM rooms r JOIN properties p ON p.id=r.property_id WHERE p.rental_mode='BY_ROOM' AND (r.room_number='WHOLE' OR r.floor_number IS NULL)"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM rooms WHERE rental_status<>'AVAILABLE' OR price<=0 OR area_m2<=0 OR max_occupants<1 OR default_deposit<0 OR electricity_price<0 OR water_price<0 OR service_fee<0"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM rooms r WHERE EXISTS (SELECT 1 FROM room_types child WHERE child.parent_id=r.room_type_id)"), "Rooms should select specific leaf types");
        assertEquals("0", scalar("WITH RECURSIVE ancestors AS (SELECT id AS root,id,parent_id,CAST(CONCAT('/',id,'/') AS CHAR(4096)) AS path,0 AS cycle FROM room_types UNION ALL SELECT a.root,p.id,p.parent_id,CONCAT(a.path,p.id,'/'),LOCATE(CONCAT('/',p.id,'/'),a.path)>0 FROM ancestors a JOIN room_types p ON p.id=a.parent_id WHERE a.cycle=0) SELECT COUNT(*) FROM ancestors WHERE cycle=1"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM (SELECT property_id,room_number FROM rooms GROUP BY property_id,room_number HAVING COUNT(*)>1) duplicated"));
    }

    @Test
    void demoCredentialsAndRolePermissionsProduceRealJwtAuthoritiesAndJpaMappingsValidate() throws SQLException {
        var encoder = new BCryptPasswordEncoder(10);
        for (String email : DEMO_EMAILS) {
            try (var statement = connection.prepareStatement("SELECT password,status FROM users WHERE email=?")) {
                statement.setString(1, email);
                try (var result = statement.executeQuery()) {
                    assertTrue(result.next(), email);
                    assertEquals("ACTIVE", result.getString("status"));
                    assertTrue(encoder.matches(DEMO_PASSWORD, result.getString("password")), email);
                }
            }
        }

        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(new DriverManagerDataSource(url(schema), user(), password()));
        factory.setPackagesToScan("com.tamdaptit.rentalmanagement");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
        try {
            factory.afterPropertiesSet();
            assertNotNull(factory.getObject());
            assertEquals(44, factory.getObject().getMetamodel().getEntities().size());
            var entityManager = factory.getObject().createEntityManager();
            try {
                var tokens = new JwtTokenServiceImpl("seed-test-key-only-".repeat(5), "seed-data-test", 60_000, 120_000);
                for (String email : DEMO_EMAILS) {
                    User user = entityManager.createQuery("SELECT u FROM User u WHERE u.email=:email", User.class)
                            .setParameter("email", email).getSingleResult();
                    Set<String> expected = new TreeSet<>();
                    if (email.startsWith("admin.")) {
                        expected.addAll(PERMISSIONS);
                        expected.add("ROLE_ADMIN");
                    } else if (email.startsWith("landlord.")) {
                        expected.addAll(Set.of("ROLE_LANDLORD", "ROOM_READ", "ROOM_CREATE", "ROOM_UPDATE", "ROOM_DELETE", "LISTING_READ", "LISTING_CREATE"));
                    } else {
                        expected.addAll(Set.of("ROLE_TENANT", "ROOM_READ", "LISTING_READ"));
                    }
                    assertEquals(expected, new TreeSet<>(tokens.buildAuthorities(user)), email);
                    String token = tokens.generateAccessToken(user);
                    assertEquals(expected, new TreeSet<>(tokens.getAuthorities(tokens.validateToken(token))), email);
                    assertEquals(user.getId(), tokens.getUserId(token));
                }
            } finally {
                entityManager.close();
            }
        } finally {
            factory.destroy();
        }
    }

    @Test
    void seedCanBeReplayedWithoutDuplicatesOrResettingAnExistingDemoPassword() throws SQLException {
        Map<String, String> initialCounts = rowCounts();
        String changedPassword = new BCryptPasswordEncoder(10).encode("ChangedDemoPassword@2026");
        try (var statement = connection.prepareStatement("UPDATE users SET password=?,auth_version=7 WHERE email=?")) {
            statement.setString(1, changedPassword);
            statement.setString(2, DEMO_EMAILS.getFirst());
            assertEquals(1, statement.executeUpdate());
        }
        applySeed();
        applySeed();
        assertEquals(initialCounts, rowCounts());
        try (var statement = connection.prepareStatement("SELECT password,auth_version FROM users WHERE email=?")) {
            statement.setString(1, DEMO_EMAILS.getFirst());
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(changedPassword, result.getString("password"));
                assertEquals(7, result.getInt("auth_version"));
            }
        }
        assertEquals("0", scalar("SELECT COUNT(*) FROM (SELECT user_id,role_id FROM user_roles GROUP BY user_id,role_id HAVING COUNT(*)>1) duplicated"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM (SELECT role_id,permission_id FROM role_permissions GROUP BY role_id,permission_id HAVING COUNT(*)>1) duplicated"));
    }

    @Test
    void actualFreshFlywayChainBuildsAndSeedsAnEmptySchemaWithoutBaseline() throws SQLException {
        Map<String, String> expectedCounts = rowCounts();
        String freshSchema = schema + "_fresh";
        try {
            try (Connection admin = DriverManager.getConnection(url(""), user(), password());
                 var statement = admin.createStatement()) {
                statement.execute("CREATE DATABASE `" + freshSchema + "` CHARACTER SET utf8mb4");
            }
            var flyway = Flyway.configure()
                    .dataSource(url(freshSchema), user(), password())
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(false)
                    .load();

            assertEquals(2, flyway.migrate().migrationsExecuted);
            try (Connection fresh = DriverManager.getConnection(url(freshSchema), user(), password())) {
                assertEquals("45", scalar(fresh, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"));
                assertEquals("1", scalar(fresh, "SELECT COUNT(*) FROM flyway_schema_history WHERE version='2' AND success=1"));
                assertEquals("0", scalar(fresh, "SELECT COUNT(*) FROM flyway_schema_history WHERE type='BASELINE'"));
                assertEquals(expectedCounts, rowCounts(fresh));
                assertEquals(0, flyway.migrate().migrationsExecuted);
                assertEquals(expectedCounts, rowCounts(fresh));
            }
        } finally {
            if (freshSchema.matches("seed_test_[a-f0-9]{32}_fresh")) {
                try (Connection admin = DriverManager.getConnection(url(""), user(), password());
                     var statement = admin.createStatement()) {
                    statement.execute("DROP DATABASE IF EXISTS `" + freshSchema + "`");
                }
            }
        }
    }
}
