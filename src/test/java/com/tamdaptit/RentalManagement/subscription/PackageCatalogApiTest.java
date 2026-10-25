package com.tamdaptit.rentalmanagement.subscription;

import com.jayway.jsonpath.JsonPath;
import com.tamdaptit.rentalmanagement.common.exception.GlobalExceptionHandler;
import com.tamdaptit.rentalmanagement.subscription.controller.ServicePackageController;
import com.tamdaptit.rentalmanagement.subscription.controller.BoostServicePackageController;
import com.tamdaptit.rentalmanagement.subscription.mapper.ServicePackageMapper;
import com.tamdaptit.rentalmanagement.subscription.mapper.BoostServicePackageMapper;
import com.tamdaptit.rentalmanagement.subscription.repository.ServicePackageRepository;
import com.tamdaptit.rentalmanagement.subscription.service.impl.ServicePackageServiceImpl;
import com.tamdaptit.rentalmanagement.subscription.service.impl.BoostServicePackageServiceImpl;
import com.tamdaptit.rentalmanagement.subscription.controller.ServiceSubscriptionController;
import com.tamdaptit.rentalmanagement.subscription.service.impl.ServiceSubscriptionServiceImpl;
import com.tamdaptit.rentalmanagement.subscription.service.ServiceSubscriptionService;
import com.tamdaptit.rentalmanagement.subscription.mapper.ServiceSubscriptionMapper;
import com.tamdaptit.rentalmanagement.subscription.dto.request.ServiceSubscriptionOrderRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.response.ServiceSubscriptionOrderResponse;
import com.tamdaptit.rentalmanagement.subscription.entity.enums.ServiceSubscriptionOrderType;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.payment.repository.PaymentRepository;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.config.SecurityConfig;
import com.tamdaptit.rentalmanagement.common.config.JwtAuthenticationEntryPoint;
import com.tamdaptit.rentalmanagement.common.security.JwtAuthenticationFilter;
import com.tamdaptit.rentalmanagement.auth.service.JwtTokenService;
import com.tamdaptit.rentalmanagement.payment.config.VnPayConfig;
import com.tamdaptit.rentalmanagement.payment.controller.PaymentController;
import com.tamdaptit.rentalmanagement.payment.mapper.PaymentMapper;
import com.tamdaptit.rentalmanagement.payment.service.VnPaySigner;
import com.tamdaptit.rentalmanagement.payment.service.PaymentService;
import com.tamdaptit.rentalmanagement.payment.service.PaymentQueryService;
import com.tamdaptit.rentalmanagement.payment.service.VnPayQueryClient;
import com.tamdaptit.rentalmanagement.payment.service.impl.PaymentQueryServiceImpl;
import com.tamdaptit.rentalmanagement.payment.service.impl.PaymentServiceImpl;
import com.tamdaptit.rentalmanagement.payment.dto.request.PaymentRequest;
import com.tamdaptit.rentalmanagement.payment.dto.response.PaymentCheckoutResponse;
import com.tamdaptit.rentalmanagement.payment.entity.enums.PaymentType;
import com.tamdaptit.rentalmanagement.listing.controller.ListingController;
import com.tamdaptit.rentalmanagement.listing.mapper.ListingMapper;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.listing.service.ListingService;
import com.tamdaptit.rentalmanagement.listing.service.impl.ListingServiceImpl;
import com.tamdaptit.rentalmanagement.listing.dto.request.ListingRequest;
import com.tamdaptit.rentalmanagement.listing.dto.response.ListingResponse;
import com.tamdaptit.rentalmanagement.listing.entity.enums.ListingStatus;
import com.tamdaptit.rentalmanagement.room.repository.RoomRepository;
import com.tamdaptit.rentalmanagement.subscription.controller.BoostPurchaseController;
import com.tamdaptit.rentalmanagement.subscription.mapper.BoostPurchaseMapper;
import com.tamdaptit.rentalmanagement.subscription.service.BoostPurchaseService;
import com.tamdaptit.rentalmanagement.subscription.service.impl.BoostPurchaseServiceImpl;
import com.tamdaptit.rentalmanagement.subscription.service.SubscriptionLifecycleService;
import com.tamdaptit.rentalmanagement.subscription.job.SubscriptionExpiryJob;
import com.tamdaptit.rentalmanagement.subscription.dto.request.BoostPurchaseRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import javax.sql.DataSource;
import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.tamdaptit.rentalmanagement.subscription.entity.enums.ServiceSubscriptionOrderStatus.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "SUBSCRIPTION_TEST_MYSQL_URL",
        matches = "jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/")
class PackageCatalogApiTest {
    private static String schema;
    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mvc;
    private TransactionStatus transaction;
    private static final TestClock clock = new TestClock();
    private static final AtomicLong providerTransactions = new AtomicLong(100000);

    private static String dbUser() {
        return System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_USER", "root");
    }

    private static String dbPassword() {
        return System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_PASSWORD", "");
    }

    private static String url(String database) {
        return System.getenv("SUBSCRIPTION_TEST_MYSQL_URL") + database
                + "?serverTimezone=Asia/Ho_Chi_Minh&useSSL=false&allowPublicKeyRetrieval=true";
    }

    @BeforeAll
    static void startContextOnIsolatedSchema() throws Exception {
        schema = "subscription_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url(""), dbUser(), dbPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
        Flyway.configure().dataSource(url(schema), dbUser(), dbPassword())
                .locations("classpath:db/migration").load().migrate();
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterAll
    static void closeContextAndDropOnlyTestSchema() throws Exception {
        if (context != null) {
            context.close();
        }
        if (schema != null && schema.matches("subscription_test_[a-f0-9]{32}")) {
            try (var connection = DriverManager.getConnection(url(""), dbUser(), dbPassword());
                 var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            }
        }
    }

    @BeforeEach
    void beginTransaction() {
        clock.set("2026-01-31T10:00:00");
        org.mockito.Mockito.reset(context.getBean(VnPayQueryClient.class));
        transaction = context.getBean(PlatformTransactionManager.class).getTransaction(new DefaultTransactionDefinition());
    }

    @AfterEach
    void rollback() {
        if (transaction != null) {
            context.getBean(PlatformTransactionManager.class).rollback(transaction);
        }
    }

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.with(user("admin").roles("ADMIN"));
    }

    private static String packageJson(String code, String max, int high, int low, int defaultLevel, boolean stats) {
        return """
                {"code":"%s","name":"Package %s","maxListings":%s,
                 "highPrioritySlots":%d,"lowPrioritySlots":%d,"defaultPriorityLevel":%d,
                 "hasStats":%s,"active":true}
                """.formatted(code, code, max, high, low, defaultLevel, stats);
    }

    private static String priceJson(int months, int price) {
        return "{\"durationMonths\":" + months + ",\"price\":" + price + ",\"active\":true}";
    }

    private static String createPackage(String code) throws Exception {
        return id(mvc.perform(admin(post("/api/service-packages")).contentType(APPLICATION_JSON)
                .content(packageJson(code, "10", 0, 5, 0, false)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static String createPrice(String packageId, int months) throws Exception {
        return id(mvc.perform(admin(post("/api/service-packages/" + packageId + "/prices"))
                .contentType(APPLICATION_JSON).content(priceJson(months, 100)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static String id(String json) {
        return JsonPath.read(json, "$.data.id");
    }

    @Test
    void adminManagesCatalogueWhileLandlordOnlySeesSaleablePackages() throws Exception {
        String packageId = createPackage("basic");
        mvc.perform(get("/api/service-packages").with(user("owner").roles("LANDLORD")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/service-packages/" + packageId).with(user("owner").roles("LANDLORD")))
                .andExpect(status().isNotFound());
        createPrice(packageId, 1);
        mvc.perform(get("/api/service-packages/" + packageId).with(user("owner").roles("LANDLORD")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.code").value("BASIC"));
        mvc.perform(admin(patch("/api/service-packages/" + packageId + "/status"))
                .contentType(APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false));
        mvc.perform(get("/api/service-packages/" + packageId).with(user("owner").roles("LANDLORD")))
                .andExpect(status().isNotFound());
        mvc.perform(admin(get("/api/service-packages/" + packageId).param("includeInactive", "true")))
                .andExpect(status().isOk());
        mvc.perform(admin(patch("/api/service-packages/" + packageId + "/status"))
                .contentType(APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/service-packages").with(user("owner").roles("LANDLORD")))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void nonAdminsCannotWriteOrRequestHiddenCatalogue() throws Exception {
        for (String role : new String[]{"LANDLORD", "TENANT"}) {
            MockHttpServletRequestBuilder[] writes = {
                    post("/api/service-packages").content(packageJson("X", "10", 0, 5, 0, false)),
                    put("/api/service-packages/missing").content(packageJson("X", "10", 0, 5, 0, false)),
                    patch("/api/service-packages/missing/status").content("{\"active\":false}"),
                    post("/api/service-packages/missing/prices").content(priceJson(1, 100)),
                    put("/api/service-packages/missing/prices/missing").content(priceJson(1, 100)),
                    patch("/api/service-packages/missing/prices/missing/status").content("{\"active\":false}"),
                    post("/api/boost-packages").content(boostJson("X", 1)),
                    put("/api/boost-packages/missing").content(boostJson("X", 1)),
                    patch("/api/boost-packages/missing/status").content("{\"active\":false}")
            };
            for (var write : writes) {
                mvc.perform(write.contentType(APPLICATION_JSON).with(user("other").roles(role)))
                        .andExpect(status().isForbidden());
            }
            mvc.perform(get("/api/service-packages").param("includeInactive", "true")
                    .with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(get("/api/boost-packages").param("includeInactive", "true")
                    .with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/service-packages")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/service-packages").with(user("tenant").roles("TENANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesRightsCodesAndBusinessUnlimitedQuota() throws Exception {
        createPackage("BASIC");
        mvc.perform(admin(post("/api/service-packages")).contentType(APPLICATION_JSON)
                .content(packageJson("basic", "10", 0, 5, 0, false))).andExpect(status().isConflict());
        mvc.perform(admin(post("/api/service-packages")).contentType(APPLICATION_JSON)
                .content(packageJson("BAD", "3", 2, 2, 0, false))).andExpect(status().isBadRequest());
        mvc.perform(admin(post("/api/service-packages")).contentType(APPLICATION_JSON)
                .content(packageJson("BUSINESS", "null", 20, 0, 1, true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.maxListings").doesNotExist())
                .andExpect(jsonPath("$.data.defaultPriorityLevel").value(1));
        mvc.perform(admin(post("/api/service-packages")).contentType(APPLICATION_JSON)
                .content(packageJson("BAD-DEFAULT", "null", 20, 1, 1, true))).andExpect(status().isBadRequest());
        mvc.perform(admin(post("/api/service-packages")).contentType(APPLICATION_JSON)
                .content(packageJson("NEGATIVE", "-1", 0, 0, 0, false))).andExpect(status().isBadRequest());
    }

    @Test
    void protectsFreeAndScopesPricesToTheirPackage() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        String freeId = jdbc.queryForObject("SELECT id FROM service_packages WHERE code='FREE'", String.class);
        mvc.perform(admin(patch("/api/service-packages/" + freeId + "/status"))
                .contentType(APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isConflict());
        mvc.perform(admin(put("/api/service-packages/" + freeId)).contentType(APPLICATION_JSON)
                .content(packageJson("RENAMED", "3", 0, 0, 0, false))).andExpect(status().isConflict());
        mvc.perform(admin(post("/api/service-packages/" + freeId + "/prices"))
                .contentType(APPLICATION_JSON).content(priceJson(1, 100))).andExpect(status().isConflict());

        String first = createPackage("FIRST");
        String priceId = createPrice(first, 1);
        String second = createPackage("SECOND");
        mvc.perform(admin(put("/api/service-packages/" + second + "/prices/" + priceId))
                .contentType(APPLICATION_JSON).content(priceJson(3, 200))).andExpect(status().isNotFound());
        mvc.perform(admin(post("/api/service-packages/" + first + "/prices"))
                .contentType(APPLICATION_JSON).content(priceJson(1, 200))).andExpect(status().isConflict());
        mvc.perform(admin(post("/api/service-packages/" + first + "/prices"))
                .contentType(APPLICATION_JSON).content(priceJson(2, 100))).andExpect(status().isBadRequest());
        mvc.perform(admin(post("/api/service-packages/" + first + "/prices"))
                .contentType(APPLICATION_JSON).content(priceJson(3, -1))).andExpect(status().isBadRequest());
        mvc.perform(admin(patch("/api/service-packages/" + first + "/prices/" + priceId + "/status"))
                .contentType(APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk());
        mvc.perform(get("/api/service-packages/" + first + "/prices").with(user("owner").roles("LANDLORD")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(admin(get("/api/service-packages/" + first + "/prices").param("includeInactive", "true")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
    }

    private static String boostJson(String code, int months) {
        return "{\"code\":\"" + code + "\",\"name\":\"High\",\"durationMonths\":" + months
                + ",\"highPrioritySlots\":1,\"lowPrioritySlots\":1,\"price\":100,\"active\":true}";
    }

    @Test
    void boostSupportsHighAndLowSlotsAndValidMonthlyTerms() throws Exception {
        String boostId = id(mvc.perform(admin(post("/api/boost-packages")).contentType(APPLICATION_JSON)
                .content(boostJson("HIGH", 3))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.highPrioritySlots").value(1)).andReturn().getResponse().getContentAsString());
        mvc.perform(admin(post("/api/boost-packages")).contentType(APPLICATION_JSON)
                .content(boostJson("BAD", 12))).andExpect(status().isBadRequest());
        mvc.perform(admin(patch("/api/boost-packages/" + boostId + "/status"))
                .contentType(APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk());
        mvc.perform(get("/api/boost-packages/" + boostId).with(user("owner").roles("LANDLORD")))
                .andExpect(status().isNotFound());
        mvc.perform(admin(put("/api/boost-packages/" + boostId)).contentType(APPLICATION_JSON)
                .content(boostJson("HIGH", 6))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durationMonths").value(6));
        mvc.perform(get("/api/boost-packages").with(user("owner").roles("LANDLORD")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void adminMustSetMonthlyTermBeforeReactivatingLegacyBoost() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.update("INSERT INTO boost_service_packages(id,code,name,price,is_active) VALUES ('legacy','LEGACY','Legacy',25,FALSE)");
        mvc.perform(admin(patch("/api/boost-packages/legacy/status"))
                .contentType(APPLICATION_JSON).content("{\"active\":true}")).andExpect(status().isBadRequest());
        mvc.perform(admin(put("/api/boost-packages/legacy")).contentType(APPLICATION_JSON)
                .content(boostJson("LEGACY", 1))).andExpect(status().isOk());
        mvc.perform(get("/api/boost-packages/legacy").with(user("owner").roles("LANDLORD")))
                .andExpect(status().isOk());
    }

    @Test
    void catalogueAndPriceUpdatesDoNotChangePurchasedSnapshots() throws Exception {
        String packageId = createPackage("BASIC");
        String priceId = createPrice(packageId, 1);
        JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.update("INSERT INTO users(id,email,password) VALUES ('owner','snapshot@example.test','test-only')");
        jdbc.update("INSERT INTO service_package_subscriptions(id,landlord_id,package_id,start_at,end_at) VALUES ('sub','owner',?,'2026-10-23','2026-11-23')", packageId);
        jdbc.update("""
                INSERT INTO service_subscription_orders(id,landlord_id,package_id,service_package_price_id,
                type,duration_months,price,package_code_snapshot,package_name_snapshot,max_listings_snapshot,
                high_priority_slots_snapshot,low_priority_slots_snapshot,default_priority_level_snapshot,has_stats_snapshot,expires_at)
                VALUES ('order','owner',?,?,'PURCHASE',1,100,'BASIC','Basic original',10,0,5,0,FALSE,'2026-12-01')
                """, packageId, priceId);
        jdbc.update("""
                INSERT INTO service_subscription_periods(id,subscription_id,order_id,period_number,start_at,end_at,
                package_code_snapshot,package_name_snapshot,max_listings_snapshot,high_priority_slots_snapshot,
                low_priority_slots_snapshot,default_priority_level_snapshot,has_stats_snapshot)
                VALUES ('period','sub','order',1,'2026-10-23','2026-11-23','BASIC','Basic original',10,0,5,0,FALSE)
                """);
        mvc.perform(admin(put("/api/service-packages/" + packageId)).contentType(APPLICATION_JSON)
                .content(packageJson("BASIC", "8", 0, 4, 0, true))).andExpect(status().isOk());
        mvc.perform(admin(put("/api/service-packages/" + packageId + "/prices/" + priceId))
                .contentType(APPLICATION_JSON).content(priceJson(1, 200))).andExpect(status().isOk());
        assertEquals(10, jdbc.queryForObject("SELECT max_listings_snapshot FROM service_subscription_periods WHERE id='period'", Integer.class));
        assertEquals("100.00", jdbc.queryForObject("SELECT CAST(price AS CHAR) FROM service_subscription_orders WHERE id='order'", String.class));
        assertEquals(8, jdbc.queryForObject("SELECT max_listings FROM service_packages WHERE id=?", Integer.class, packageId));
    }

    private static ServiceSubscriptionService subscriptions() {
        return context.getBean(ServiceSubscriptionService.class);
    }

    private static JdbcTemplate jdbc() {
        return new JdbcTemplate(context.getBean(DataSource.class));
    }

    private static void createOwners() {
        jdbc().update("INSERT INTO users(id,email,password) VALUES ('owner','owner@example.test','test-only'),"
                + "('other','other@example.test','test-only'),('admin','admin@example.test','test-only')");
    }

    private static ServiceSubscriptionOrderResponse order(String priceId, ServiceSubscriptionOrderType type) {
        return subscriptions().createOrder("owner", new ServiceSubscriptionOrderRequest(priceId, type));
    }

    private static void pay(ServiceSubscriptionOrderResponse order) {
        pay(order, LocalDateTime.now(clock));
    }

    private static void pay(ServiceSubscriptionOrderResponse order, LocalDateTime paidAt) {
        jdbc().update("""
                INSERT INTO payments(id,landlord_id,type,service_subscription_order_id,merchant_order_id,
                expires_at,amount,payment_method,status,paid_at)
                VALUES (?,?,'SERVICE_PACKAGE',?,?,?,?,'VNPAY','SUCCESS',?)
                """, UUID.randomUUID().toString(), order.landlordId(), order.id(), UUID.randomUUID().toString(),
                order.expiresAt(), order.price(), paidAt);
    }

    private static ServiceSubscriptionOrderResponse paid(String priceId, ServiceSubscriptionOrderType type) {
        var order = order(priceId, type);
        pay(order);
        return subscriptions().applyPaidOrder("owner", order.id());
    }

    @Test
    void orderUsesServerSnapshotAndNeverGrantsRightsBeforePayment() throws Exception {
        createOwners();
        String packageId = createPackage("BASIC");
        String priceId = createPrice(packageId, 1);
        mvc.perform(post("/api/subscriptions/orders").with(user("owner").roles("LANDLORD"))
                .contentType(APPLICATION_JSON).content("""
                        {"priceOptionId":"%s","type":"PURCHASE","price":1,"landlordId":"other"}
                        """.formatted(priceId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.landlordId").value("owner"))
                .andExpect(jsonPath("$.data.price").value(100))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));
        assertTrue(subscriptions().getCurrent("owner").free());
        assertEquals(0, jdbc().queryForObject("SELECT COUNT(*) FROM service_subscription_periods", Integer.class));
        mvc.perform(get("/api/subscriptions/current").with(user("owner").roles("LANDLORD")))
                .andExpect(jsonPath("$.data.rights.maxListings").value(3));
        mvc.perform(post("/api/subscriptions/orders").with(user("tenant").roles("TENANT"))
                .contentType(APPLICATION_JSON).content("{\"priceOptionId\":\"x\",\"type\":\"PURCHASE\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/subscriptions/orders/awaiting-confirmation").with(user("owner").roles("LANDLORD")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/subscriptions/orders").with(user("owner").roles("LANDLORD"))
                .contentType(APPLICATION_JSON).content("{\"type\":\"PURCHASE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void purchasesAndRenewalsUseCalendarMonthsAndImmutablePeriodSnapshots() throws Exception {
        createOwners();
        String packageId = createPackage("BASIC");
        String priceId = createPrice(packageId, 1);
        var first = paid(priceId, ServiceSubscriptionOrderType.PURCHASE);
        assertEquals(LocalDateTime.parse("2026-02-28T10:00:00"), first.endAt());
        assertEquals(first, subscriptions().applyPaidOrder("owner", first.id()));
        mvc.perform(admin(put("/api/service-packages/" + packageId)).contentType(APPLICATION_JSON)
                .content(packageJson("BASIC", "8", 0, 4, 0, true))).andExpect(status().isOk());
        var renewal = paid(priceId, ServiceSubscriptionOrderType.RENEW);
        assertEquals(first.subscriptionId(), renewal.subscriptionId());
        assertEquals(first.endAt(), renewal.effectiveAt());
        assertEquals(LocalDateTime.parse("2026-03-28T10:00:00"), renewal.endAt());
        assertEquals(10, subscriptions().getCurrent("owner").rights().maxListings());
        assertEquals(renewal.endAt(), subscriptions().getCurrent("owner").subscriptionEndsAt());
        assertEquals(8, subscriptions().getCurrent("owner").nextPeriod().rights().maxListings());
        mvc.perform(get("/api/subscriptions/history").with(user("owner").roles("LANDLORD")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/subscriptions/periods").with(user("other").roles("LANDLORD")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/subscriptions/orders").with(user("owner").roles("LANDLORD")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
        clock.set("2026-02-28T10:00:00");
        assertEquals(8, subscriptions().getCurrent("owner").rights().maxListings());
        assertTrue(subscriptions().getCurrent("owner").rights().hasStats());
        assertEquals(2, subscriptions().getPeriods("owner", 0, 10).getTotalElements());
        clock.set("2026-03-28T10:00:00");
        assertFalse(subscriptions().getCurrent("owner").canSubmitListings());
        assertFalse(subscriptions().getCurrent("owner").free());
    }

    @Test
    void separatePurchasesSupportAllTermsAndLeapYear() throws Exception {
        createOwners();
        String packageId = createPackage("BASIC");
        String priceId = createPrice(packageId, 12);
        clock.set("2024-02-29T10:00:00");
        var annual = paid(priceId, ServiceSubscriptionOrderType.PURCHASE);
        assertEquals(LocalDateTime.parse("2025-02-28T10:00:00"), annual.endAt());
        clock.set("2025-03-01T10:00:00");
        var three = paid(createPrice(packageId, 3), ServiceSubscriptionOrderType.RENEW);
        assertEquals(LocalDateTime.parse("2025-06-01T10:00:00"), three.endAt());
        assertNotEquals(annual.subscriptionId(), three.subscriptionId());
        var six = paid(createPrice(packageId, 6), ServiceSubscriptionOrderType.RENEW);
        assertEquals(LocalDateTime.parse("2025-12-01T10:00:00"), six.endAt());
    }

    @Test
    void nonUpgradesScheduleAutomaticallyAtOldEndAndBlockRenewals() throws Exception {
        createOwners();
        String priceId = createPrice(createPackage("BASIC"), 1);
        var first = paid(priceId, ServiceSubscriptionOrderType.PURCHASE);
        var renewalWaitingForPayment = order(priceId, ServiceSubscriptionOrderType.RENEW);
        String nextPrice = createPrice(createPackage("NEXT"), 3);
        var change = paid(nextPrice, ServiceSubscriptionOrderType.CHANGE);
        assertEquals(SCHEDULED, change.status());
        assertEquals(first.subscriptionId(), subscriptions().getCurrent("owner").subscriptionId());
        mvc.perform(admin(get("/api/subscriptions/orders/awaiting-confirmation")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(admin(post("/api/subscriptions/orders/" + change.id() + "/confirm")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SCHEDULED"));
        var confirmed = subscriptions().getOrder("owner", change.id());
        assertEquals(first.endAt(), confirmed.effectiveAt());
        assertEquals(confirmed, subscriptions().confirmChange("admin", change.id()));
        pay(renewalWaitingForPayment);
        assertEquals(REQUIRES_REVIEW, subscriptions().applyPaidOrder("owner", renewalWaitingForPayment.id()).status());
        assertEquals(first.endAt(), subscriptions().getCurrent("owner").subscriptionEndsAt());
        clock.set("2026-02-28T10:00:00");
        assertEquals(confirmed.subscriptionId(), subscriptions().getCurrent("owner").subscriptionId());
        assertEquals(APPLIED, subscriptions().getOrder("owner", change.id()).status());
        assertEquals(confirmed.effectiveAt(), subscriptions().confirmChange("admin", change.id()).effectiveAt());
    }

    @Test
    void scheduledChangeBlocksCreatingRenewalAndAnotherChange() throws Exception {
        createOwners();
        String priceId = createPrice(createPackage("BASIC"), 1);
        paid(priceId, ServiceSubscriptionOrderType.PURCHASE);
        String nextPrice = createPrice(createPackage("NEXT"), 1);
        var change = paid(nextPrice, ServiceSubscriptionOrderType.CHANGE);
        subscriptions().confirmChange("admin", change.id());
        assertThrows(AppException.class, () -> order(priceId, ServiceSubscriptionOrderType.RENEW));
        assertThrows(AppException.class, () -> order(nextPrice, ServiceSubscriptionOrderType.CHANGE));
    }

    @Test
    void delayedSynchronizationPreservesAutomaticallyScheduledChangeDates() throws Exception {
        createOwners();
        String priceId = createPrice(createPackage("BASIC"), 1);
        clock.set("2026-10-30T10:00:00");
        paid(priceId, ServiceSubscriptionOrderType.PURCHASE);
        var change = paid(createPrice(createPackage("NEXT"), 1), ServiceSubscriptionOrderType.CHANGE);
        clock.set("2026-12-03T10:00:00");
        var confirmed = subscriptions().confirmChange("admin", change.id());
        assertEquals(APPLIED, confirmed.status());
        assertEquals(LocalDateTime.parse("2026-11-30T10:00:00"), confirmed.effectiveAt());
        assertEquals(LocalDateTime.parse("2026-12-30T10:00:00"), confirmed.endAt());
        assertEquals(confirmed, subscriptions().confirmChange("admin", change.id()));
        var anotherChange = paid(priceId, ServiceSubscriptionOrderType.CHANGE);
        var scheduled = subscriptions().confirmChange("admin", anotherChange.id());
        clock.set("2027-05-01T10:00:00");
        assertFalse(subscriptions().getCurrent("owner").canSubmitListings());
        var completed = subscriptions().getOrder("owner", scheduled.id());
        assertEquals(APPLIED, completed.status());
        assertEquals(scheduled.effectiveAt(), completed.effectiveAt());
        assertEquals(scheduled.endAt(), completed.endAt());
    }

    @Test
    void unpaidOrderCannotApplyOrBeConfirmedAndOtherOwnersCannotReadIt() throws Exception {
        createOwners();
        String priceId = createPrice(createPackage("BASIC"), 1);
        var pending = order(priceId, ServiceSubscriptionOrderType.PURCHASE);
        assertThrows(AppException.class, () -> subscriptions().applyPaidOrder("owner", pending.id()));
        mvc.perform(get("/api/subscriptions/orders/" + pending.id()).with(user("other").roles("LANDLORD")))
                .andExpect(status().isNotFound());
        mvc.perform(admin(post("/api/subscriptions/orders/" + pending.id() + "/confirm")))
                .andExpect(status().isConflict());
        assertEquals(0, jdbc().queryForObject("SELECT COUNT(*) FROM service_package_subscriptions", Integer.class));
    }

    @Test
    void expiredChangeOrderReleasesReservationButLatePaymentNeedsReview() throws Exception {
        createOwners();
        String priceId = createPrice(createPackage("BASIC"), 1);
        paid(priceId, ServiceSubscriptionOrderType.PURCHASE);
        String nextPrice = createPrice(createPackage("NEXT"), 1);
        var old = order(nextPrice, ServiceSubscriptionOrderType.CHANGE);
        clock.set("2026-01-31T10:15:00");
        var next = order(nextPrice, ServiceSubscriptionOrderType.CHANGE);
        assertEquals(EXPIRED, subscriptions().getOrder("owner", old.id()).status());
        pay(old);
        assertEquals(REQUIRES_REVIEW, subscriptions().applyPaidOrder("owner", old.id()).status());
        assertEquals(PENDING_PAYMENT, subscriptions().getOrder("owner", next.id()).status());
    }

    @Test
    void timelyPaymentCanApplyWhenCallbackArrivesAfterOrderExpiry() throws Exception {
        createOwners();
        var pending = order(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        clock.set("2026-01-31T11:00:00");
        assertEquals(EXPIRED, subscriptions().getOrder("owner", pending.id()).status());
        // IPN arrives after expiration, but provider payment time was within the allowed window.
        pay(pending, LocalDateTime.parse("2026-01-31T10:05:00"));
        var applied = subscriptions().applyPaidOrder("owner", pending.id());
        assertEquals(APPLIED, applied.status());
        assertEquals(LocalDateTime.parse("2026-01-31T11:00:00"), applied.effectiveAt());
    }

    @Test
    void delayedPaidChangeDoesNotOverrideAnOpenReplacementChange() throws Exception {
        createOwners();
        paid(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        String nextPrice = createPrice(createPackage("NEXT"), 1);
        var first = order(nextPrice, ServiceSubscriptionOrderType.CHANGE);
        clock.set("2026-01-31T11:00:00");
        var replacement = order(nextPrice, ServiceSubscriptionOrderType.CHANGE);
        pay(first, LocalDateTime.parse("2026-01-31T10:05:00"));
        assertEquals(REQUIRES_REVIEW, subscriptions().applyPaidOrder("owner", first.id()).status());
        assertEquals(PENDING_PAYMENT, subscriptions().getOrder("owner", replacement.id()).status());
    }

    @Test
    void purchasedFuturePeriodSurvivesLaterCatalogChangesAndInactivePricesCannotBeBought() throws Exception {
        createOwners();
        String packageId = createPackage("BASIC");
        String priceId = createPrice(packageId, 1);
        paid(priceId, ServiceSubscriptionOrderType.PURCHASE);
        var renewal = paid(priceId, ServiceSubscriptionOrderType.RENEW);
        mvc.perform(admin(put("/api/service-packages/" + packageId)).contentType(APPLICATION_JSON)
                .content(packageJson("BASIC", "6", 0, 3, 0, true))).andExpect(status().isOk());
        clock.set(renewal.effectiveAt().toString());
        assertEquals(10, subscriptions().getCurrent("owner").rights().maxListings());
        mvc.perform(admin(patch("/api/service-packages/" + packageId + "/prices/" + priceId + "/status"))
                .contentType(APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk());
        assertThrows(AppException.class, () -> order(priceId, ServiceSubscriptionOrderType.RENEW));
    }

    @Test
    void simultaneousChangeRequestsAllowOnlyOneOpenOrder() throws Exception {
        createOwners();
        String priceId = createPrice(createPackage("BASIC"), 1);
        paid(priceId, ServiceSubscriptionOrderType.PURCHASE);
        String nextPrice = createPrice(createPackage("NEXT"), 1);
        context.getBean(PlatformTransactionManager.class).commit(transaction);
        transaction = null;
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> create = () -> {
                try {
                    order(nextPrice, ServiceSubscriptionOrderType.CHANGE);
                    return true;
                } catch (AppException expected) {
                    return false;
                }
            };
            var one = executor.submit(create);
            var two = executor.submit(create);
            assertNotEquals(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
            assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM service_subscription_orders WHERE type='CHANGE'", Integer.class));
        } finally {
            removeCommittedFixtures();
        }
    }

    @Test
    void confirmationAndRenewalRaceKeepsTheConfirmedScheduleConsistent() throws Exception {
        createOwners();
        String priceId = createPrice(createPackage("BASIC"), 1);
        var first = paid(priceId, ServiceSubscriptionOrderType.PURCHASE);
        var renewal = order(priceId, ServiceSubscriptionOrderType.RENEW);
        pay(renewal);
        var change = paid(createPrice(createPackage("NEXT"), 1), ServiceSubscriptionOrderType.CHANGE);
        context.getBean(PlatformTransactionManager.class).commit(transaction);
        transaction = null;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var confirmation = executor.submit(() -> subscriptions().confirmChange("admin", change.id()));
            var renewing = executor.submit(() -> subscriptions().applyPaidOrder("owner", renewal.id()));
            var confirmed = confirmation.get(15, TimeUnit.SECONDS);
            var renewed = renewing.get(15, TimeUnit.SECONDS);
            assertEquals(SCHEDULED, confirmed.status());
            if (renewed.status() == APPLIED) {
                assertEquals(renewed.endAt(), confirmed.effectiveAt());
            } else {
                assertEquals(REQUIRES_REVIEW, renewed.status());
                assertEquals(first.endAt(), confirmed.effectiveAt());
            }
            assertEquals(confirmed.effectiveAt(), subscriptions().getCurrent("owner").subscriptionEndsAt());
            assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM service_package_subscriptions WHERE status='SCHEDULED'", Integer.class));
        } finally {
            removeCommittedFixtures();
        }
    }

    @Test
    void simultaneousRenewalsAppendDisjointPeriodsExactlyOnce() throws Exception {
        createOwners();
        String priceId = createPrice(createPackage("BASIC"), 1);
        paid(priceId, ServiceSubscriptionOrderType.PURCHASE);
        var one = order(priceId, ServiceSubscriptionOrderType.RENEW);
        var two = order(priceId, ServiceSubscriptionOrderType.RENEW);
        pay(one);
        pay(two);
        context.getBean(PlatformTransactionManager.class).commit(transaction);
        transaction = null;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> subscriptions().applyPaidOrder("owner", one.id()));
            var second = executor.submit(() -> subscriptions().applyPaidOrder("owner", two.id()));
            var a = first.get(15, TimeUnit.SECONDS);
            var b = second.get(15, TimeUnit.SECONDS);
            assertTrue(a.endAt().equals(b.effectiveAt()) || b.endAt().equals(a.effectiveAt()));
            assertEquals(3, subscriptions().getPeriods("owner", 0, 10).getTotalElements());
            assertEquals(LocalDateTime.parse("2026-04-28T10:00:00"), subscriptions().getCurrent("owner").subscriptionEndsAt());
            subscriptions().applyPaidOrder("owner", one.id());
            assertEquals(3, subscriptions().getPeriods("owner", 0, 10).getTotalElements());
        } finally {
            removeCommittedFixtures();
        }
    }

    private static void removeCommittedFixtures() {
        // Concurrency tests commit fixtures for worker transactions, in this class's isolated schema only.
        jdbc().update("DELETE FROM invoices");
        jdbc().update("UPDATE listings SET service_subscription_period_id=NULL,package_priority_level=0");
        jdbc().update("DELETE FROM service_subscription_periods");
        jdbc().update("DELETE FROM payments");
        jdbc().update("DELETE FROM boost_allocations");
        jdbc().update("DELETE FROM boost_service_package_subscriptions");
        jdbc().update("DELETE FROM listings");
        jdbc().update("DELETE FROM service_subscription_orders");
        jdbc().update("DELETE FROM service_package_subscriptions");
        jdbc().update("DELETE FROM service_package_prices");
        jdbc().update("DELETE FROM service_packages WHERE code <> 'FREE'");
        jdbc().update("DELETE FROM boost_service_packages");
        jdbc().update("DELETE FROM rooms");
        jdbc().update("DELETE FROM room_types");
        jdbc().update("DELETE FROM wards");
        jdbc().update("DELETE FROM provinces");
        jdbc().update("DELETE FROM users WHERE id IN ('owner','other','admin')");
    }

    private static ListingService listings() { return context.getBean(ListingService.class); }
    private static PaymentService payments() { return context.getBean(PaymentService.class); }
    private static BoostPurchaseService boosts() { return context.getBean(BoostPurchaseService.class); }

    private static void roomFixtures(int count) {
        jdbc().update("INSERT INTO provinces(id,code,name) VALUES ('p-test','P-TEST','Test')");
        jdbc().update("INSERT INTO wards(id,province_id,code,name) VALUES ('w-test','p-test','W-TEST','Test')");
        jdbc().update("INSERT INTO room_types(id,name) VALUES ('rt-test','Test room')");
        for (int i = 1; i <= count; i++) {
            jdbc().update("""
                    INSERT INTO rooms(id,landlord_id,room_type_id,price,area_m2,province_id,ward_id,address_line)
                    VALUES (?,'owner','rt-test',100,20,'p-test','w-test','Test address')
                    """, "room-" + i);
        }
    }

    private static ListingResponse submitRoom(int room) {
        return listings().create("owner", new ListingRequest("room-" + room, "Room " + room, "Test content"));
    }

    private static ListingResponse publishRoom(int room) {
        return listings().approve("admin", submitRoom(room).id());
    }

    private static PaymentCheckoutResponse checkout(String target, PaymentType type) {
        return payments().checkout("owner", new PaymentRequest(type, target), "127.0.0.1");
    }

    private static MultiValueMap<String, String> callback(PaymentCheckoutResponse checkout, String code) {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.set("vnp_TmnCode", "TESTCODE");
        parameters.set("vnp_TxnRef", checkout.payment().merchantOrderId());
        parameters.set("vnp_Amount", checkout.payment().amount().movePointRight(2).toBigIntegerExact().toString());
        parameters.set("vnp_ResponseCode", code);
        parameters.set("vnp_TransactionStatus", code);
        parameters.set("vnp_TransactionNo", Long.toString(providerTransactions.incrementAndGet()));
        parameters.set("vnp_PayDate", LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("uuuuMMddHHmmss")));
        sign(parameters);
        return parameters;
    }

    private static void sign(MultiValueMap<String, String> parameters) {
        parameters.set("vnp_SecureHash", context.getBean(VnPaySigner.class).sign(parameters.toSingleValueMap()));
    }

    private static com.tamdaptit.rentalmanagement.subscription.service.BoostAllocationService allocations() {
        return context.getBean(com.tamdaptit.rentalmanagement.subscription.service.BoostAllocationService.class);
    }

    private static void assignBoost(String listingId, String boostId, int level) {
        allocations().assign("owner", listingId,
                new com.tamdaptit.rentalmanagement.subscription.dto.request.BoostAllocationRequest(boostId, level));
    }

    private static String createBoost(int months) throws Exception {
        return id(mvc.perform(admin(post("/api/boost-packages")).contentType(APPLICATION_JSON)
                .content(boostJson("BOOST_" + months, months))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static void ipn(MultiValueMap<String, String> parameters, String expected) throws Exception {
        mvc.perform(get("/api/payments/vnpay/ipn").params(parameters))
                .andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value(expected));
    }

    @Test
    void checkoutReturnAndIpnUseActualSecurityAndSettleExactlyOnce() throws Exception {
        createOwners();
        var order = order(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        var checkout = checkout(order.id(), PaymentType.SERVICE_PACKAGE);
        assertTrue(checkout.paymentUrl().contains("vnp_Amount=10000"));
        assertTrue(checkout.paymentUrl().contains("vnp_Version=2.1.0"));
        assertTrue(checkout.paymentUrl().contains("vnp_ExpireDate=20260131101500"));
        assertEquals(checkout.payment().id(), checkout(order.id(), PaymentType.SERVICE_PACKAGE).payment().id());
        var callback = callback(checkout, "00");
        mvc.perform(get("/api/payments/vnpay/return").params(callback)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signatureValid").value(true));
        assertTrue(subscriptions().getCurrent("owner").free());
        assertEquals("PENDING", payments().getById("owner", checkout.payment().id(), false).status().name());
        ipn(callback, "00");
        ipn(callback, "02");
        assertEquals(1, subscriptions().getPeriods("owner", 0, 10).getTotalElements());
        assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM invoices", Integer.class));
        mvc.perform(get("/api/payments/" + checkout.payment().id())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/payments/" + checkout.payment().id()).with(user("other").roles("LANDLORD")))
                .andExpect(status().isNotFound());
        mvc.perform(admin(get("/api/payments/" + checkout.payment().id() + "/invoice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalAmount").value(100));
        mvc.perform(post("/api/payments/vnpay/checkout").contentType(APPLICATION_JSON)
                .content("{\"type\":\"SERVICE_PACKAGE\",\"targetId\":\"" + order.id() + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/payments/vnpay/ipn")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureTerminalReferenceAmountAndDuplicateParametersNeverGrantRights() throws Exception {
        createOwners();
        var order = order(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        var checkout = checkout(order.id(), PaymentType.SERVICE_PACKAGE);
        var badSignature = callback(checkout, "00");
        badSignature.set("vnp_Amount", "1");
        ipn(badSignature, "97");
        var badAmount = callback(checkout, "00");
        badAmount.set("vnp_Amount", "10100"); sign(badAmount); ipn(badAmount, "04");
        var badTerminal = callback(checkout, "00");
        badTerminal.set("vnp_TmnCode", "OTHER"); sign(badTerminal); ipn(badTerminal, "97");
        var badReference = callback(checkout, "00");
        badReference.set("vnp_TxnRef", "unknown"); sign(badReference); ipn(badReference, "01");
        var duplicated = callback(checkout, "00"); duplicated.add("vnp_Amount", "1"); ipn(duplicated, "97");
        assertTrue(subscriptions().getCurrent("owner").free());
        assertEquals(0, jdbc().queryForObject("SELECT COUNT(*) FROM invoices", Integer.class));
    }

    @Test
    void failedAttemptCanRetryAndDuplicateCollectionIsRetainedForReview() throws Exception {
        createOwners();
        var order = order(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        var first = checkout(order.id(), PaymentType.SERVICE_PACKAGE);
        var failed = callback(first, "24");
        ipn(failed, "00"); ipn(failed, "02");
        assertEquals("FAILED", payments().getById("owner", first.payment().id(), false).status().name());
        var second = checkout(order.id(), PaymentType.SERVICE_PACKAGE);
        assertNotEquals(first.payment().id(), second.payment().id());
        ipn(callback(second, "00"), "00");
        ipn(callback(first, "00"), "00");
        assertTrue(payments().getById("owner", first.payment().id(), false).requiresReview());
        assertEquals(2, jdbc().queryForObject("SELECT COUNT(*) FROM payments WHERE status='SUCCESS'", Integer.class));
        assertEquals(2, jdbc().queryForObject("SELECT COUNT(*) FROM invoices", Integer.class));
        assertEquals(1, subscriptions().getPeriods("owner", 0, 10).getTotalElements());
        mvc.perform(admin(get("/api/payments/review"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void moneyPaidAfterDeadlineIsRecordedWithoutGrantingRights() throws Exception {
        createOwners();
        var order = order(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        var checkout = checkout(order.id(), PaymentType.SERVICE_PACKAGE);
        clock.set("2026-01-31T10:15:00");
        ipn(callback(checkout, "00"), "00");
        var payment = payments().getById("owner", checkout.payment().id(), false);
        assertEquals("SUCCESS", payment.status().name());
        assertTrue(payment.requiresReview());
        assertEquals(REQUIRES_REVIEW, subscriptions().getOrder("owner", order.id()).status());
        assertTrue(subscriptions().getCurrent("owner").free());
        assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM invoices", Integer.class));
    }

    @Test
    void delayedIpnAfterExpiryJobHonorsTheOriginalProviderPaymentTime() throws Exception {
        createOwners();
        var order = order(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        var checkout = checkout(order.id(), PaymentType.SERVICE_PACKAGE);
        var callback = callback(checkout, "00");
        clock.set("2026-01-31T11:00:00");
        context.getBean(SubscriptionExpiryJob.class).refreshDueOwners();
        assertEquals(EXPIRED, subscriptions().getOrder("owner", order.id()).status());
        ipn(callback, "00");
        assertFalse(payments().getById("owner", checkout.payment().id(), false).requiresReview());
        assertEquals(APPLIED, subscriptions().getOrder("owner", order.id()).status());
    }

    @Test
    void paidDeferredChangeSchedulesAutomaticallyAndLateRenewalPaymentGoesToReview() throws Exception {
        createOwners();
        String price = createPrice(createPackage("BASIC"), 1);
        paid(price, ServiceSubscriptionOrderType.PURCHASE);
        var renewal = order(price, ServiceSubscriptionOrderType.RENEW);
        var renewalPayment = checkout(renewal.id(), PaymentType.SERVICE_PACKAGE);
        var change = order(createPrice(createPackage("NEXT"), 3), ServiceSubscriptionOrderType.CHANGE);
        ipn(callback(checkout(change.id(), PaymentType.SERVICE_PACKAGE), "00"), "00");
        assertEquals(SCHEDULED, subscriptions().getOrder("owner", change.id()).status());
        var scheduled = subscriptions().confirmChange("admin", change.id());
        ipn(callback(renewalPayment, "00"), "00");
        assertTrue(payments().getById("owner", renewalPayment.payment().id(), false).requiresReview());
        assertEquals(scheduled.effectiveAt(), subscriptions().getCurrent("owner").subscriptionEndsAt());
    }

    @Test
    void freeQuotaCountsPendingAndPublishedWithoutCountingApprovalTwice() throws Exception {
        createOwners(); roomFixtures(4);
        var first = submitRoom(1); submitRoom(2); submitRoom(3);
        var published = listings().approve("admin", first.id());
        assertEquals(ListingStatus.PUBLISHED, published.status());
        assertThrows(AppException.class, () -> submitRoom(4));
        listings().hide("owner", first.id());
        assertEquals(ListingStatus.PENDING, submitRoom(4).status());
        assertThrows(AppException.class, () -> listings().create("other", new ListingRequest("room-1", "Invalid", null)));
        mvc.perform(post("/api/listings/" + first.id() + "/approve").with(user("owner").roles("LANDLORD")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/listings/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    void pendingHoldsSevenDaysAndRepostAlwaysRequiresFreshApproval() {
        createOwners(); roomFixtures(4);
        var pending = submitRoom(1); submitRoom(2); submitRoom(3);
        clock.set("2026-02-07T10:00:00");
        assertThrows(AppException.class, () -> listings().approve("admin", pending.id()));
        submitRoom(4);
        var resubmitted = listings().submit("owner", pending.id());
        assertEquals(LocalDateTime.parse("2026-02-14T10:00:00"), resubmitted.pendingExpiresAt());
        clock.set("2026-02-13T10:00:00");
        var published = listings().approve("admin", pending.id());
        assertEquals(LocalDateTime.parse("2026-03-13T10:00:00"), published.expiresAt());
        clock.set("2026-03-13T10:00:00");
        assertEquals(0, listings().getPublished(0, 10).getTotalElements());
        assertEquals(ListingStatus.PENDING, listings().submit("owner", published.id()).status());
        assertEquals(0, listings().getPublished(0, 10).getTotalElements());
        assertEquals(LocalDateTime.parse("2026-04-13T10:00:00"), listings().approve("admin", pending.id()).expiresAt());
    }

    @Test
    void expiredPaidPackageKeepsPublishedListingsButPreventsSubmittingAndApproving() throws Exception {
        createOwners(); roomFixtures(3);
        paid(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        clock.set("2026-02-20T10:00:00");
        var published = publishRoom(1);
        clock.set("2026-02-27T10:00:00");
        var pending = submitRoom(2);
        clock.set("2026-03-01T10:00:00");
        assertEquals(published.id(), listings().getPublished(0, 10).getData().getFirst().id());
        assertThrows(AppException.class, () -> submitRoom(3));
        assertThrows(AppException.class, () -> listings().approve("admin", pending.id()));
    }

    @Test
    void prioritySlotsCanBeMovedAndFeedUsesPriorityThenNewestPublishTime() throws Exception {
        createOwners(); roomFixtures(3);
        String pack = id(mvc.perform(admin(post("/api/service-packages")).contentType(APPLICATION_JSON)
                .content(packageJson("SLOTS", "5", 1, 1, 0, false))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        paid(createPrice(pack, 3), ServiceSubscriptionOrderType.PURCHASE);
        var one = publishRoom(1);
        clock.set("2026-01-31T10:01:00"); var two = publishRoom(2);
        clock.set("2026-01-31T10:02:00"); var three = publishRoom(3);
        listings().setPriority("owner", one.id(), 2); listings().setPriority("owner", two.id(), 1);
        assertEquals(List.of(one.id(), two.id(), three.id()), listings().getPublished(0, 10).getData().stream().map(ListingResponse::id).toList());
        assertThrows(AppException.class, () -> listings().setPriority("owner", three.id(), 2));
        listings().setPriority("owner", one.id(), 0); listings().setPriority("owner", three.id(), 2);
        listings().setPriority("owner", two.id(), 0);
        assertEquals(List.of(three.id(), two.id(), one.id()), listings().getPublished(0, 10).getData().stream().map(ListingResponse::id).toList());
    }

    @Test
    void businessHasUnlimitedListingsTwentyHighSlotsAndAutomaticLowPriority() throws Exception {
        createOwners(); roomFixtures(22);
        String pack = id(mvc.perform(admin(post("/api/service-packages")).contentType(APPLICATION_JSON)
                .content(packageJson("BUSINESS", "null", 20, 0, 1, true))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        paid(createPrice(pack, 1), ServiceSubscriptionOrderType.PURCHASE);
        for (int i = 1; i <= 20; i++) {
            var listing = publishRoom(i);
            assertEquals(1, listing.priorityLevel());
            listings().setPriority("owner", listing.id(), 2);
        }
        var low = publishRoom(21); publishRoom(22);
        assertEquals(22, listings().getPublished(0, 100).getTotalElements());
        assertEquals(20, listings().getPublished(0, 100).getData().stream().filter(l -> l.priorityLevel() == 2).count());
        assertEquals(1, listings().getPublished(0, 100).getData().getLast().priorityLevel());
        assertThrows(AppException.class, () -> listings().setPriority("owner", low.id(), 2));
    }

    @Test
    void boostSurvivesPackageExpiryButNeverExtendsTheListing() throws Exception {
        createOwners(); roomFixtures(2);
        paid(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        clock.set("2026-02-20T10:00:00");
        var listing = publishRoom(1);
        var boost = boosts().create("owner", new BoostPurchaseRequest(createBoost(3)));
        ipn(callback(checkout(boost.id(), PaymentType.BOOST_PACKAGE), "00"), "00");
        assignBoost(listing.id(), boost.id(), 2);
        clock.set("2026-03-01T10:00:00");
        assertEquals(2, listings().getPublishedById(listing.id()).priorityLevel());
        assertEquals(listing.expiresAt(), listings().getPublishedById(listing.id()).expiresAt());
        assertThrows(AppException.class, () -> submitRoom(2));
        clock.set("2026-03-20T10:00:00");
        assertEquals(0, listings().getPublished(0, 10).getTotalElements());
        assertEquals(LocalDateTime.parse("2026-05-20T10:00:00"), boosts().getHistory("owner", 0, 10).getData().getFirst().endAt());
    }

    @Test
    void boostExpiryFallsBackToCurrentPackageSelectionAndAllowsAnotherPurchase() throws Exception {
        createOwners(); roomFixtures(1);
        paid(createPrice(createPackage("BASIC"), 6), ServiceSubscriptionOrderType.PURCHASE);
        var listing = publishRoom(1);
        String boostPackage = createBoost(1);
        var boost = boosts().create("owner", new BoostPurchaseRequest(boostPackage));
        ipn(callback(checkout(boost.id(), PaymentType.BOOST_PACKAGE), "00"), "00");
        clock.set("2026-02-10T10:00:00");
        listings().hide("owner", listing.id()); listings().submit("owner", listing.id()); listings().approve("admin", listing.id());
        listings().setPriority("owner", listing.id(), 1);
        assignBoost(listing.id(), boost.id(), 2);
        assertEquals(2, listings().getPublishedById(listing.id()).priorityLevel());
        clock.set("2026-02-28T10:00:00");
        assertEquals(1, listings().getPublishedById(listing.id()).priorityLevel());
        var next = boosts().create("owner", new BoostPurchaseRequest(boostPackage));
        ipn(callback(checkout(next.id(), PaymentType.BOOST_PACKAGE), "00"), "00");
        assignBoost(listing.id(), next.id(), 2);
        assertEquals(2, listings().getPublishedById(listing.id()).priorityLevel());
        assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM boost_service_package_subscriptions WHERE status='ACTIVE'", Integer.class));
    }

    @Test
    void periodBoundaryDropsOldSlotsAndJobDoesNotResetQuotaOrHideOldListings() throws Exception {
        createOwners(); roomFixtures(3);
        String pack = createPackage("BASIC");
        String price = createPrice(pack, 1);
        paid(price, ServiceSubscriptionOrderType.PURCHASE);
        clock.set("2026-02-20T10:00:00");
        var listing = publishRoom(1); listings().setPriority("owner", listing.id(), 1);
        mvc.perform(admin(put("/api/service-packages/" + pack)).contentType(APPLICATION_JSON)
                .content(packageJson("BASIC", "1", 0, 0, 0, false))).andExpect(status().isOk());
        paid(price, ServiceSubscriptionOrderType.RENEW);
        publishRoom(2);
        clock.set("2026-02-28T10:00:00");
        assertEquals(0, listings().getPublishedById(listing.id()).priorityLevel());
        context.getBean(SubscriptionExpiryJob.class).refreshDueOwners();
        context.getBean(SubscriptionExpiryJob.class).refreshDueOwners();
        assertEquals(2, listings().getPublished(0, 10).getTotalElements());
        assertEquals(2, subscriptions().getPeriods("owner", 0, 10).getTotalElements());
        assertEquals(0, jdbc().queryForObject("SELECT package_priority_level FROM listings WHERE id=?", Integer.class, listing.id()));
        assertThrows(AppException.class, () -> submitRoom(3));
    }

    @Test
    void concurrentFreeSubmissionsCannotExceedTheLastAvailableQuotaSlot() throws Exception {
        createOwners(); roomFixtures(4); submitRoom(1); submitRoom(2);
        context.getBean(PlatformTransactionManager.class).commit(transaction); transaction = null;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> { try { submitRoom(3); return true; } catch (AppException e) { return false; } });
            var two = executor.submit(() -> { try { submitRoom(4); return true; } catch (AppException e) { return false; } });
            assertNotEquals(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
            assertEquals(3, listings().getMine("owner", 0, 10).getTotalElements());
        } finally { removeCommittedFixtures(); }
    }

    @Test
    void concurrentBoostPaymentsActivateBothIndependentPackages() throws Exception {
        createOwners(); roomFixtures(1);
        var listing = publishRoom(1); String catalog = createBoost(6);
        var one = boosts().create("owner", new BoostPurchaseRequest(catalog));
        var two = boosts().create("owner", new BoostPurchaseRequest(catalog));
        var a = callback(checkout(one.id(), PaymentType.BOOST_PACKAGE), "00");
        var b = callback(checkout(two.id(), PaymentType.BOOST_PACKAGE), "00");
        context.getBean(PlatformTransactionManager.class).commit(transaction); transaction = null;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> payments().processIpn(a));
            var second = executor.submit(() -> payments().processIpn(b));
            assertEquals("00", first.get(15, TimeUnit.SECONDS).code());
            assertEquals("00", second.get(15, TimeUnit.SECONDS).code());
            assertEquals(2, jdbc().queryForObject("SELECT COUNT(*) FROM boost_service_package_subscriptions WHERE status='ACTIVE'", Integer.class));
            assertEquals(0, jdbc().queryForObject("SELECT COUNT(*) FROM payments WHERE requires_review=TRUE", Integer.class));
            assertEquals(2, jdbc().queryForObject("SELECT COUNT(*) FROM invoices", Integer.class));
            assertEquals("2026-07-31 10:00:00", jdbc().queryForObject("SELECT CAST(MAX(end_at) AS CHAR) FROM boost_service_package_subscriptions WHERE status='ACTIVE'", String.class));
        } finally { removeCommittedFixtures(); }
    }

    @Test
    void simultaneousDuplicateIpnCreatesOnePeriodAndOneInvoice() throws Exception {
        createOwners();
        var order = order(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        var callback = callback(checkout(order.id(), PaymentType.SERVICE_PACKAGE), "00");
        context.getBean(PlatformTransactionManager.class).commit(transaction); transaction = null;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> payments().processIpn(callback));
            var two = executor.submit(() -> payments().processIpn(callback));
            assertEquals(java.util.Set.of("00", "02"), java.util.Set.of(one.get(15, TimeUnit.SECONDS).code(), two.get(15, TimeUnit.SECONDS).code()));
            assertEquals(1, subscriptions().getPeriods("owner", 0, 10).getTotalElements());
            assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM invoices", Integer.class));
        } finally { removeCommittedFixtures(); }
    }

    @Test
    void simultaneousPriorityAssignmentsCannotExceedOneHighSlot() throws Exception {
        createOwners(); roomFixtures(2);
        String pack = id(mvc.perform(admin(post("/api/service-packages")).contentType(APPLICATION_JSON)
                .content(packageJson("SLOTS", "5", 1, 1, 0, false))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        paid(createPrice(pack, 3), ServiceSubscriptionOrderType.PURCHASE);
        var a = publishRoom(1); var b = publishRoom(2);
        context.getBean(PlatformTransactionManager.class).commit(transaction); transaction = null;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> { try { listings().setPriority("owner", a.id(), 2); return true; } catch (AppException e) { return false; } });
            var two = executor.submit(() -> { try { listings().setPriority("owner", b.id(), 2); return true; } catch (AppException e) { return false; } });
            assertNotEquals(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
            assertEquals(1, listings().getPublished(0, 10).getData().stream().filter(l -> l.priorityLevel() == 2).count());
        } finally { removeCommittedFixtures(); }
    }

    @Test
    void anotherOwnerCannotAllocateBoostAndHiddenListingsCannotBeAssigned() throws Exception {
        createOwners(); roomFixtures(1);
        var order = order(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        assertThrows(AppException.class, () -> payments().checkout("other", new PaymentRequest(PaymentType.SERVICE_PACKAGE, order.id()), "127.0.0.1"));
        var listing = publishRoom(1);
        var boost = boosts().create("owner", new BoostPurchaseRequest(createBoost(1)));
        ipn(callback(checkout(boost.id(), PaymentType.BOOST_PACKAGE), "00"), "00");
        assertThrows(AppException.class, () -> allocations().assign("other", listing.id(),
                new com.tamdaptit.rentalmanagement.subscription.dto.request.BoostAllocationRequest(boost.id(), 2)));
        listings().hide("owner", listing.id());
        assertThrows(AppException.class, () -> assignBoost(listing.id(), boost.id(), 2));
    }
    @Test
    void duplicateCollectionDoesNotBlockConfirmingTheLegitimateChangePayment() throws Exception {
        createOwners();
        paid(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        var change = order(createPrice(createPackage("NEXT"), 1), ServiceSubscriptionOrderType.CHANGE);
        var first = checkout(change.id(), PaymentType.SERVICE_PACKAGE);
        ipn(callback(first, "24"), "00");
        var second = checkout(change.id(), PaymentType.SERVICE_PACKAGE);
        ipn(callback(second, "00"), "00");
        ipn(callback(first, "00"), "00");
        assertTrue(payments().getById("owner", first.payment().id(), false).requiresReview());
        assertEquals(SCHEDULED, subscriptions().confirmChange("admin", change.id()).status());
    }

    @Test
    void checkoutRejectsInvalidRenewalsButAllowsMultipleBoostPackages() throws Exception {
        createOwners(); roomFixtures(1);
        String price = createPrice(createPackage("BASIC"), 1);
        paid(price, ServiceSubscriptionOrderType.PURCHASE);
        var renewal = order(price, ServiceSubscriptionOrderType.RENEW);
        var change = paid(createPrice(createPackage("NEXT"), 1), ServiceSubscriptionOrderType.CHANGE);
        subscriptions().confirmChange("admin", change.id());
        assertThrows(AppException.class, () -> checkout(renewal.id(), PaymentType.SERVICE_PACKAGE));
        var listing = publishRoom(1); String catalog = createBoost(1);
        var one = boosts().create("owner", new BoostPurchaseRequest(catalog));
        var two = boosts().create("owner", new BoostPurchaseRequest(catalog));
        ipn(callback(checkout(one.id(), PaymentType.BOOST_PACKAGE), "00"), "00");
        ipn(callback(checkout(two.id(), PaymentType.BOOST_PACKAGE), "00"), "00");
    }

    @Test
    void responseCodeAloneCannotTurnAFailedProviderTransactionIntoSuccess() throws Exception {
        createOwners();
        var order = order(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        var checkout = checkout(order.id(), PaymentType.SERVICE_PACKAGE);
        var callback = callback(checkout, "00");
        callback.set("vnp_TransactionStatus", "01"); sign(callback);
        ipn(callback, "00");
        assertEquals("FAILED", payments().getById("owner", checkout.payment().id(), false).status().name());
        assertTrue(subscriptions().getCurrent("owner").free());
        assertEquals(0, jdbc().queryForObject("SELECT COUNT(*) FROM invoices", Integer.class));
    }

    @Test
    void checkoutRejectsFractionalVndWithoutRoundingOrCreatingAPayment() throws Exception {
        createOwners();
        String pack = createPackage("BASIC"); String price = createPrice(pack, 1);
        mvc.perform(admin(put("/api/service-packages/" + pack + "/prices/" + price)).contentType(APPLICATION_JSON)
                .content("{\"durationMonths\":1,\"price\":100.5,\"active\":true}")).andExpect(status().isOk());
        var order = order(price, ServiceSubscriptionOrderType.PURCHASE);
        assertThrows(AppException.class, () -> checkout(order.id(), PaymentType.SERVICE_PACKAGE));
        assertEquals(0, jdbc().queryForObject("SELECT COUNT(*) FROM payments", Integer.class));
    }

    @Test
    void concurrentDuplicateCollectionsCannotOverwriteAnAppliedOrderFromAnOlderReadSnapshot() throws Exception {
        createOwners();
        var order = order(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        var first = checkout(order.id(), PaymentType.SERVICE_PACKAGE);
        ipn(callback(first, "24"), "00");
        var second = checkout(order.id(), PaymentType.SERVICE_PACKAGE);
        var a = callback(first, "00"); var b = callback(second, "00");
        context.getBean(PlatformTransactionManager.class).commit(transaction); transaction = null;
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        var transactions = new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        java.util.function.Function<MultiValueMap<String, String>, String> notify = parameters -> transactions.execute(status -> {
            jdbc().queryForObject("SELECT COUNT(*) FROM service_subscription_orders WHERE status='PENDING_PAYMENT'", Integer.class);
            try { barrier.await(5, TimeUnit.SECONDS); } catch (Exception e) { throw new IllegalStateException(e); }
            return payments().processIpn(parameters).code();
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> notify.apply(a));
            var two = executor.submit(() -> notify.apply(b));
            assertEquals("00", one.get(15, TimeUnit.SECONDS));
            assertEquals("00", two.get(15, TimeUnit.SECONDS));
            assertEquals(APPLIED, subscriptions().getOrder("owner", order.id()).status());
            assertEquals(1, subscriptions().getPeriods("owner", 0, 10).getTotalElements());
            assertEquals(2, jdbc().queryForObject("SELECT COUNT(*) FROM invoices", Integer.class));
            assertEquals(1, payments().getReviewQueue(0, 10).getTotalElements());
        } finally { removeCommittedFixtures(); }
    }

    private static Map<String, String> queryResult(PaymentCheckoutResponse payment, String status) {
        var fields = new java.util.HashMap<>(callback(payment, "00").toSingleValueMap());
        fields.put("vnp_Command", "querydr");
        fields.put("vnp_TransactionType", "01");
        fields.put("vnp_TransactionStatus", status);
        return Map.copyOf(fields);
    }

    private static VnPayQueryClient queryClient() { return context.getBean(VnPayQueryClient.class); }

    private void commitFixtures() {
        context.getBean(PlatformTransactionManager.class).commit(transaction);
        transaction = null;
    }

    private static void mockQuery(Map<String, String> result) {
        org.mockito.Mockito.doAnswer(invocation -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return result;
        }).when(queryClient()).query(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reopenedCheckoutKeepsOriginalTransactionDateForQueryDr() throws Exception {
        createOwners();
        var order = order(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        var first = checkout(order.id(), PaymentType.SERVICE_PACKAGE);
        clock.set("2026-01-31T10:02:00");
        var reopened = checkout(order.id(), PaymentType.SERVICE_PACKAGE);
        assertEquals(first.payment().id(), reopened.payment().id());
        assertTrue(reopened.paymentUrl().contains("vnp_CreateDate=20260131100000"));
        assertEquals(LocalDateTime.parse("2026-01-31T10:00:00"), payments().getQuerySnapshot("owner", first.payment().id(), false).transactionDate());
    }

    @Test
    void queryDrRecoversExpiredPaymentAndRepeatedChecksDoNotCreateMoreRights() throws Exception {
        createOwners();
        var order = order(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        var payment = checkout(order.id(), PaymentType.SERVICE_PACKAGE);
        var verified = queryResult(payment, "00");
        clock.set("2026-01-31T11:00:00");
        context.getBean(SubscriptionExpiryJob.class).refreshDueOwners();
        commitFixtures();
        try {
            mockQuery(verified);
            mvc.perform(post("/api/payments/" + payment.payment().id() + "/check").with(user("owner").roles("LANDLORD")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.payment.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.payment.requiresReview").value(false))
                    .andExpect(jsonPath("$.data.providerTransactionStatus").value("00"));
            context.getBean(PaymentQueryService.class).check("owner", payment.payment().id(), false);
            org.mockito.Mockito.verify(queryClient(), org.mockito.Mockito.times(1)).query(org.mockito.ArgumentMatchers.any());
            assertEquals(1, subscriptions().getPeriods("owner", 0, 10).getTotalElements());
            assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM invoices", Integer.class));
        } finally { removeCommittedFixtures(); }
    }

    @Test
    void queryDrDoesNotAllowAnotherOwnerTenantOrAnonymousToInspectThePayment() throws Exception {
        createOwners();
        var order = order(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        var payment = checkout(order.id(), PaymentType.SERVICE_PACKAGE);
        commitFixtures();
        try {
            mvc.perform(post("/api/payments/" + payment.payment().id() + "/check").with(user("other").roles("LANDLORD")))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/payments/" + payment.payment().id() + "/check").with(user("tenant").roles("TENANT")))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/payments/" + payment.payment().id() + "/check")).andExpect(status().isUnauthorized());
            org.mockito.Mockito.verifyNoInteractions(queryClient());
        } finally { removeCommittedFixtures(); }
    }

    @Test
    void queryDrPendingAndNetworkErrorsNeverCreateSuccessAndConfirmedFailureIsRecorded() throws Exception {
        createOwners();
        var order = order(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        var payment = checkout(order.id(), PaymentType.SERVICE_PACKAGE);
        var pending = queryResult(payment, "01");
        commitFixtures();
        try {
            mockQuery(pending);
            var result = context.getBean(PaymentQueryService.class).check("owner", payment.payment().id(), false);
            assertEquals("PENDING", result.payment().status().name());
            org.mockito.Mockito.when(queryClient().query(org.mockito.ArgumentMatchers.any()))
                    .thenThrow(new AppException(com.tamdaptit.rentalmanagement.common.exception.ErrorCode.VNPAY_QUERY_FAILED));
            mvc.perform(post("/api/payments/" + payment.payment().id() + "/check").with(user("owner").roles("LANDLORD")))
                    .andExpect(status().isBadGateway());
            assertEquals("PENDING", payments().getById("owner", payment.payment().id(), false).status().name());
            var failed = new java.util.HashMap<>(pending);
            failed.put("vnp_TransactionStatus", "02");
            mockQuery(failed);
            assertEquals("FAILED", context.getBean(PaymentQueryService.class).check("owner", payment.payment().id(), false).payment().status().name());
            assertEquals(0, jdbc().queryForObject("SELECT COUNT(*) FROM invoices", Integer.class));
        } finally { removeCommittedFixtures(); }
    }

    @Test
    void queryDrAndIpnCannotDoubleApplyWhenIpnArrivesDuringTheNetworkRequest() throws Exception {
        createOwners();
        var order = order(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        var payment = checkout(order.id(), PaymentType.SERVICE_PACKAGE);
        var callback = callback(payment, "00");
        var verified = new java.util.HashMap<>(callback.toSingleValueMap());
        verified.put("vnp_TransactionType", "01"); verified.put("vnp_Command", "querydr");
        commitFixtures();
        var queryStarted = new java.util.concurrent.CountDownLatch(1);
        var releaseQuery = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.when(queryClient().query(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            queryStarted.countDown();
            assertTrue(releaseQuery.await(10, TimeUnit.SECONDS));
            return verified;
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var checking = executor.submit(() -> context.getBean(PaymentQueryService.class).check("owner", payment.payment().id(), false));
            assertTrue(queryStarted.await(5, TimeUnit.SECONDS));
            assertEquals("00", executor.submit(() -> payments().processIpn(callback)).get(5, TimeUnit.SECONDS).code());
            releaseQuery.countDown();
            var result = checking.get(10, TimeUnit.SECONDS);
            assertEquals("SUCCESS", result.payment().status().name());
            assertTrue(result.payment().invoiceId() != null);
            assertEquals(1, subscriptions().getPeriods("owner", 0, 10).getTotalElements());
            assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM invoices", Integer.class));
        } finally { releaseQuery.countDown(); removeCommittedFixtures(); }
    }

    @Test
    void adminQueryDrCanConfirmPaymentButPlanChangeStillNeedsSeparateAdminConfirmation() throws Exception {
        createOwners();
        paid(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        var change = order(createPrice(createPackage("NEXT"), 1), ServiceSubscriptionOrderType.CHANGE);
        var payment = checkout(change.id(), PaymentType.SERVICE_PACKAGE);
        var verified = queryResult(payment, "00");
        commitFixtures();
        try {
            mockQuery(verified);
            mvc.perform(admin(post("/api/payments/" + payment.payment().id() + "/check")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.payment.status").value("SUCCESS"));
            assertEquals(SCHEDULED, subscriptions().getOrder("owner", change.id()).status());
            assertEquals(SCHEDULED, subscriptions().confirmChange("admin", change.id()).status());
        } finally { removeCommittedFixtures(); }
    }

    @Test
    void queryDrCanRecoverACancelledPendingBoostWithTimelyPayment() throws Exception {
        createOwners(); roomFixtures(1);
        var listing = publishRoom(1);
        var boost = boosts().create("owner", new BoostPurchaseRequest(createBoost(1)));
        var payment = checkout(boost.id(), PaymentType.BOOST_PACKAGE);
        var verified = queryResult(payment, "00");
        clock.set("2026-01-31T11:00:00");
        context.getBean(SubscriptionExpiryJob.class).refreshDueOwners();
        commitFixtures();
        try {
            mockQuery(verified);
            context.getBean(PaymentQueryService.class).check("owner", payment.payment().id(), false);
            assertEquals(0, listings().getPublishedById(listing.id()).priorityLevel());
            assignBoost(listing.id(), boost.id(), 2);
            assertEquals(2, listings().getPublishedById(listing.id()).priorityLevel());
            assertEquals("ACTIVE", boosts().getHistory("owner", 0, 10).getData().getFirst().status().name());
        } finally { removeCommittedFixtures(); }
    }

    @Test
    void queryDrRetainsLateCollectionsForReviewAndDoesNotGrantRights() throws Exception {
        createOwners();
        var order = order(createPrice(createPackage("BASIC"), 1), ServiceSubscriptionOrderType.PURCHASE);
        var payment = checkout(order.id(), PaymentType.SERVICE_PACKAGE);
        clock.set("2026-01-31T10:16:00");
        var verified = queryResult(payment, "00");
        commitFixtures();
        try {
            mockQuery(verified);
            var result = context.getBean(PaymentQueryService.class).check("owner", payment.payment().id(), false);
            assertTrue(result.payment().requiresReview());
            assertEquals("SUCCESS", result.payment().status().name());
            assertTrue(subscriptions().getCurrent("owner").free());
            assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM invoices", Integer.class));
        } finally { removeCommittedFixtures(); }
    }

    @Test
    void boostPurchasedBeforeListingsCanAllocateBothLevelsAndReleaseSlots() throws Exception {
        createOwners();
        String boostId = id(mvc.perform(post("/api/boost-subscriptions").with(user("owner").roles("LANDLORD"))
                .contentType(APPLICATION_JSON).content("{\"boostPackageId\":\"" + createBoost(1) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.highPrioritySlotsSnapshot").value(1))
                .andReturn().getResponse().getContentAsString());
        ipn(callback(checkout(boostId, PaymentType.BOOST_PACKAGE), "00"), "00");
        roomFixtures(3);
        var high = publishRoom(1); var low = publishRoom(2); var normal = publishRoom(3);
        assertEquals(0, listings().getPublishedById(high.id()).priorityLevel());
        mvc.perform(put("/api/boost-subscriptions/allocations/" + high.id()).with(user("owner").roles("LANDLORD"))
                .contentType(APPLICATION_JSON).content("{\"boostSubscriptionId\":\"" + boostId + "\",\"level\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.level").value(2));
        assignBoost(low.id(), boostId, 1);
        assertEquals(List.of(2, 1, 0), listings().getPublished(0, 10).getData().stream().map(ListingResponse::priorityLevel).toList());
        mvc.perform(get("/api/boost-subscriptions/active").with(user("owner").roles("LANDLORD")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].remainingHighPrioritySlots").value(0))
                .andExpect(jsonPath("$.data[0].remainingLowPrioritySlots").value(0));
        mvc.perform(put("/api/boost-subscriptions/allocations/" + normal.id()).with(user("owner").roles("LANDLORD"))
                .contentType(APPLICATION_JSON).content("{\"boostSubscriptionId\":\"" + boostId + "\",\"level\":2}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/boost-subscriptions/allocations/" + high.id()).with(user("owner").roles("LANDLORD")))
                .andExpect(status().isOk());
        assignBoost(normal.id(), boostId, 2);
        listings().hide("owner", low.id());
        assertEquals(1, allocations().getActive("owner").getFirst().remainingLowPrioritySlots());
        assertEquals(0, listings().getPublishedById(high.id()).priorityLevel());
        assertEquals(high.expiresAt(), listings().getPublishedById(high.id()).expiresAt());
    }

    @Test
    void boostAllocationsTransferBetweenPackagesAndUseMaximumOfServiceAndBoost() throws Exception {
        createOwners(); roomFixtures(2);
        String pack = id(mvc.perform(admin(post("/api/service-packages")).contentType(APPLICATION_JSON)
                .content(packageJson("HIGH", "5", 1, 1, 0, false))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        paid(createPrice(pack, 3), ServiceSubscriptionOrderType.PURCHASE);
        var high = publishRoom(1); var low = publishRoom(2);
        listings().setPriority("owner", high.id(), 2);
        String catalog = createBoost(1);
        var first = boosts().create("owner", new BoostPurchaseRequest(catalog));
        var second = boosts().create("owner", new BoostPurchaseRequest(catalog));
        ipn(callback(checkout(first.id(), PaymentType.BOOST_PACKAGE), "00"), "00");
        ipn(callback(checkout(second.id(), PaymentType.BOOST_PACKAGE), "00"), "00");
        assignBoost(high.id(), first.id(), 1);
        assertEquals(2, listings().getPublishedById(high.id()).priorityLevel());
        assignBoost(high.id(), second.id(), 2);
        assignBoost(low.id(), first.id(), 1);
        assertEquals(2, jdbc().queryForObject("SELECT COUNT(*) FROM boost_allocations", Integer.class));
        assertEquals(1, allocations().getActive("owner").stream().filter(b -> b.subscription().id().equals(first.id()))
                .findFirst().orElseThrow().remainingHighPrioritySlots());
        assignBoost(high.id(), second.id(), 2); // Repeating the same assignment consumes no extra slot.
        assertEquals(2, jdbc().queryForObject("SELECT COUNT(*) FROM boost_allocations", Integer.class));
    }

    @Test
    void onlyTheExpiredBoostLosesItsAllocationsAndSnapshotSurvivesCatalogEdits() throws Exception {
        createOwners(); roomFixtures(2);
        var first = boosts().create("owner", new BoostPurchaseRequest(createBoost(1)));
        var second = boosts().create("owner", new BoostPurchaseRequest(createBoost(3)));
        ipn(callback(checkout(first.id(), PaymentType.BOOST_PACKAGE), "00"), "00");
        ipn(callback(checkout(second.id(), PaymentType.BOOST_PACKAGE), "00"), "00");
        clock.set("2026-02-10T10:00:00");
        var a = publishRoom(1); var b = publishRoom(2);
        assignBoost(a.id(), first.id(), 2); assignBoost(b.id(), second.id(), 1);
        jdbc().update("UPDATE boost_service_packages SET high_priority_slots=9,low_priority_slots=9");
        assertTrue(allocations().getActive("owner").stream().allMatch(p -> p.subscription().highPrioritySlotsSnapshot() == 1));
        clock.set("2026-02-28T10:00:00");
        assertEquals(0, listings().getPublishedById(a.id()).priorityLevel());
        assertEquals(1, listings().getPublishedById(b.id()).priorityLevel());
        context.getBean(SubscriptionExpiryJob.class).refreshDueOwners();
        assertEquals(1, allocations().getActive("owner").size());
        assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM boost_allocations", Integer.class));
    }

    @Test
    void simultaneousBoostAssignmentsCannotExceedThePurchasedHighSlots() throws Exception {
        createOwners(); roomFixtures(2);
        var boost = boosts().create("owner", new BoostPurchaseRequest(createBoost(1)));
        ipn(callback(checkout(boost.id(), PaymentType.BOOST_PACKAGE), "00"), "00");
        var a = publishRoom(1); var b = publishRoom(2);
        commitFixtures();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> { try { assignBoost(a.id(), boost.id(), 2); return true; } catch (AppException e) { return false; } });
            var two = executor.submit(() -> { try { assignBoost(b.id(), boost.id(), 2); return true; } catch (AppException e) { return false; } });
            assertNotEquals(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
            assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM boost_allocations", Integer.class));
        } finally { removeCommittedFixtures(); }
    }

    @Test
    void boostCatalogAndAllocationsRejectInvalidSlotsLevelsAndUnpaidPackages() throws Exception {
        createOwners(); roomFixtures(1);
        for (String invalid : List.of(
                boostJson("EMPTY", 1).replace("\"highPrioritySlots\":1,\"lowPrioritySlots\":1", "\"highPrioritySlots\":0,\"lowPrioritySlots\":0"),
                boostJson("NEGATIVE", 1).replace("\"highPrioritySlots\":1", "\"highPrioritySlots\":-1"),
                boostJson("MISSING", 1).replace("\"lowPrioritySlots\":1,", ""))) {
            mvc.perform(admin(post("/api/boost-packages")).contentType(APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        var listing = publishRoom(1);
        var boost = boosts().create("owner", new BoostPurchaseRequest(createBoost(1)));
        String body = "{\"boostSubscriptionId\":\"" + boost.id() + "\",\"level\":2}";
        mvc.perform(put("/api/boost-subscriptions/allocations/" + listing.id()).with(user("owner").roles("LANDLORD"))
                .contentType(APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/boost-subscriptions/allocations/" + listing.id()).with(user("owner").roles("LANDLORD"))
                .contentType(APPLICATION_JSON).content(body.replace("\"level\":2", "\"level\":0")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/boost-subscriptions/active").with(user("tenant").roles("TENANT")))
                .andExpect(status().isForbidden());
        assertEquals(0, jdbc().queryForObject("SELECT COUNT(*) FROM boost_allocations", Integer.class));
    }

    private static String tierPrice(String code, String max, int high, int low, int defaultLevel, boolean stats, int amount) throws Exception {
        String pack = id(mvc.perform(admin(post("/api/service-packages")).contentType(APPLICATION_JSON)
                .content(packageJson(code, max, high, low, defaultLevel, stats))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return id(mvc.perform(admin(post("/api/service-packages/" + pack + "/prices")).contentType(APPLICATION_JSON)
                .content(priceJson(1, amount))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void paidUpgradeImmediatelyRaisesQuotaPreservesPriorityAndKeepsOriginalExpiry() throws Exception {
        createOwners(); roomFixtures(21);
        String basic = tierPrice("TEN", "10", 1, 5, 0, false, 100);
        String better = tierPrice("TWENTY", "20", 2, 5, 0, true, 200);
        var original = paid(basic, ServiceSubscriptionOrderType.PURCHASE);
        clock.set("2026-02-14T10:00:00");
        var listing = publishRoom(1);
        listings().setPriority("owner", listing.id(), 2);
        for (int i = 2; i <= 10; i++) submitRoom(i);
        var upgrade = order(better, ServiceSubscriptionOrderType.CHANGE);
        assertTrue(upgrade.immediateUpgrade());
        assertEquals(0, upgrade.price().compareTo(new java.math.BigDecimal("50")));
        assertEquals(10, subscriptions().getCurrent("owner").rights().maxListings());
        var payment = checkout(upgrade.id(), PaymentType.SERVICE_PACKAGE);
        var callback = callback(payment, "00");
        ipn(callback, "00");
        var applied = subscriptions().getOrder("owner", upgrade.id());
        assertEquals(APPLIED, applied.status());
        assertEquals(original.endAt(), applied.endAt());
        assertEquals(LocalDateTime.parse("2026-02-14T10:00:00"), applied.effectiveAt());
        assertEquals(20, subscriptions().getCurrent("owner").rights().maxListings());
        assertEquals(2, listings().getPublishedById(listing.id()).priorityLevel());
        assertEquals(listing.expiresAt(), listings().getPublishedById(listing.id()).expiresAt());
        for (int i = 11; i <= 20; i++) submitRoom(i);
        mvc.perform(post("/api/listings").with(user("owner").roles("LANDLORD")).contentType(APPLICATION_JSON)
                .content("{\"roomId\":\"room-21\",\"title\":\"Over limit\"}"))
                .andExpect(status().isBadRequest());
        ipn(callback, "02");
        assertEquals(2, subscriptions().getPeriods("owner", 0, 10).getTotalElements());
        assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM invoices", Integer.class));
        var renewal = paid(better, ServiceSubscriptionOrderType.RENEW);
        assertEquals(applied.endAt(), renewal.effectiveAt());
    }

    @Test
    void downgradeSchedulesWithoutAdminAndKeepsHigherRightsUntilExpiry() throws Exception {
        createOwners();
        String basic = tierPrice("TEN", "10", 1, 5, 0, false, 100);
        String better = tierPrice("BUSINESS", "null", 2, 0, 1, true, 200);
        var original = paid(better, ServiceSubscriptionOrderType.PURCHASE);
        clock.set("2026-02-14T10:00:00");
        var downgrade = paid(basic, ServiceSubscriptionOrderType.CHANGE);
        assertFalse(downgrade.immediateUpgrade());
        assertEquals(SCHEDULED, downgrade.status());
        assertEquals(original.endAt(), downgrade.effectiveAt());
        assertEquals("BUSINESS", subscriptions().getCurrent("owner").rights().packageCode());
        clock.set(original.endAt().toString());
        assertEquals("TEN", subscriptions().getCurrent("owner").rights().packageCode());
        assertEquals(APPLIED, subscriptions().getOrder("owner", downgrade.id()).status());
    }

    @Test
    void upgradeQuotesCarryForwardPrepaidRenewalsAndRepeatedUpgradeCredits() throws Exception {
        createOwners();
        String basic = tierPrice("TEN", "10", 1, 5, 0, false, 100);
        String better = tierPrice("TWENTY", "20", 2, 5, 0, true, 200);
        String best = tierPrice("BUSINESS", "null", 3, 0, 1, true, 300);
        paid(basic, ServiceSubscriptionOrderType.PURCHASE);
        var renewal = paid(basic, ServiceSubscriptionOrderType.RENEW);
        clock.set("2026-02-14T10:00:00");
        var upgrade = paid(better, ServiceSubscriptionOrderType.CHANGE);
        assertEquals(0, upgrade.price().compareTo(new java.math.BigDecimal("150")));
        assertEquals(renewal.endAt(), upgrade.endAt());
        assertEquals(0, jdbc().queryForObject("SELECT entitlement_value FROM service_subscription_periods WHERE order_id=?",
                java.math.BigDecimal.class, upgrade.id()).compareTo(new java.math.BigDecimal("300")));
        var next = paid(best, ServiceSubscriptionOrderType.CHANGE);
        assertEquals(0, next.price().compareTo(new java.math.BigDecimal("150")));
        assertEquals(renewal.endAt(), next.endAt());
        assertEquals("BUSINESS", subscriptions().getCurrent("owner").rights().packageCode());
    }

    @Test
    void upgradeWithNoExtraChargeAppliesWithoutVnpayAndRetainsPaidCredit() throws Exception {
        createOwners();
        String basic = tierPrice("TEN", "10", 1, 5, 0, false, 100);
        String better = tierPrice("TWENTY", "20", 2, 5, 0, true, 100);
        clock.set("2026-01-15T10:00:00");
        var original = paid(basic, ServiceSubscriptionOrderType.PURCHASE);
        clock.set("2026-02-01T10:00:00");
        var upgrade = order(better, ServiceSubscriptionOrderType.CHANGE);
        assertTrue(upgrade.immediateUpgrade());
        assertEquals(0, upgrade.price().signum());
        assertEquals(APPLIED, upgrade.status());
        assertEquals(original.endAt(), upgrade.endAt());
        assertEquals(20, subscriptions().getCurrent("owner").rights().maxListings());
        assertEquals(upgrade, subscriptions().applyPaidOrder("owner", upgrade.id()));
        assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM payments", Integer.class));
    }

    @Test
    void upgradeUsesTheOriginalCalendarPeriodAndSupportsDifferentPriceTerms() throws Exception {
        createOwners();
        String basic = tierPrice("TEN", "10", 1, 5, 0, false, 100);
        String better = tierPrice("TWENTY", "20", 2, 5, 0, true, 200);
        String pack = jdbc().queryForObject("SELECT package_id FROM service_package_prices WHERE id=?", String.class, better);
        String threeMonths = id(mvc.perform(admin(post("/api/service-packages/" + pack + "/prices"))
                .contentType(APPLICATION_JSON).content(priceJson(3, 600))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        clock.set("2026-01-15T10:00:00");
        var original = paid(basic, ServiceSubscriptionOrderType.PURCHASE);
        clock.set("2026-02-01T10:00:00");
        // 14/31 of the original month remains. (600/3 - 100) * 14/31 rounds up to 46 VND.
        var upgrade = paid(threeMonths, ServiceSubscriptionOrderType.CHANGE);
        assertTrue(upgrade.immediateUpgrade());
        assertEquals(0, upgrade.price().compareTo(new java.math.BigDecimal("46")));
        assertEquals(original.endAt(), upgrade.endAt());
    }

    @Test
    void renewalAppliedAfterUpgradeQuoteMakesTheOldQuoteRequireReview() throws Exception {
        createOwners();
        String basic = tierPrice("TEN", "10", 1, 5, 0, false, 100);
        String better = tierPrice("TWENTY", "20", 2, 5, 0, true, 200);
        paid(basic, ServiceSubscriptionOrderType.PURCHASE);
        var renewal = order(basic, ServiceSubscriptionOrderType.RENEW);
        var upgrade = order(better, ServiceSubscriptionOrderType.CHANGE);
        var payment = checkout(upgrade.id(), PaymentType.SERVICE_PACKAGE);
        pay(renewal);
        subscriptions().applyPaidOrder("owner", renewal.id());
        ipn(callback(payment, "00"), "00");
        assertEquals(REQUIRES_REVIEW, subscriptions().getOrder("owner", upgrade.id()).status());
        assertTrue(payments().getById("owner", payment.payment().id(), false).requiresReview());
        assertEquals(10, subscriptions().getCurrent("owner").rights().maxListings());
    }

    private static class TestClock extends Clock {
        private volatile Instant instant;

        void set(String localDateTime) {
            instant = LocalDateTime.parse(localDateTime).atZone(getZone()).toInstant();
        }

        @Override public ZoneId getZone() { return ZoneId.of("Asia/Ho_Chi_Minh"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {ServicePackageRepository.class, UserRepository.class,
            PaymentRepository.class, ListingRepository.class, RoomRepository.class})
    @Import({ServicePackageController.class, BoostServicePackageController.class,
            ServicePackageServiceImpl.class, BoostServicePackageServiceImpl.class, GlobalExceptionHandler.class,
            ServiceSubscriptionController.class, ServiceSubscriptionServiceImpl.class,
            PaymentController.class, PaymentServiceImpl.class, ListingController.class, ListingServiceImpl.class,
            BoostPurchaseController.class, BoostPurchaseServiceImpl.class, com.tamdaptit.rentalmanagement.subscription.service.BoostAllocationService.class, SubscriptionLifecycleService.class,
            SubscriptionExpiryJob.class, SecurityConfig.class, JwtAuthenticationEntryPoint.class, PaymentQueryServiceImpl.class})
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() { return new ObjectMapper(); }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(UserRepository users) {
            return new JwtAuthenticationFilter(org.mockito.Mockito.mock(JwtTokenService.class), users);
        }

        @Bean
        VnPayConfig vnPayConfig() {
            return new VnPayConfig("TESTCODE", "test-only-signature-key", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                    "http://localhost:8080/api/payments/vnpay/return", "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction", "127.0.0.1");
        }

        @Bean
        VnPayQueryClient vnPayQueryClient() { return org.mockito.Mockito.mock(VnPayQueryClient.class); }

        @Bean
        VnPaySigner vnPaySigner(VnPayConfig config) { return new VnPaySigner(config); }

        @Bean
        PaymentMapper paymentMapper() { return Mappers.getMapper(PaymentMapper.class); }

        @Bean
        ListingMapper listingMapper() { return Mappers.getMapper(ListingMapper.class); }

        @Bean
        BoostPurchaseMapper boostPurchaseMapper() { return Mappers.getMapper(BoostPurchaseMapper.class); }

        @Bean
        Clock subscriptionClock() {
            return clock;
        }

        @Bean
        ServiceSubscriptionMapper serviceSubscriptionMapper() {
            return Mappers.getMapper(ServiceSubscriptionMapper.class);
        }

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(url(schema), dbUser(), dbPassword());
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.tamdaptit.rentalmanagement");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean
        ServicePackageMapper servicePackageMapper() {
            return Mappers.getMapper(ServicePackageMapper.class);
        }

        @Bean
        BoostServicePackageMapper boostServicePackageMapper() {
            return Mappers.getMapper(BoostServicePackageMapper.class);
        }

    }
}
