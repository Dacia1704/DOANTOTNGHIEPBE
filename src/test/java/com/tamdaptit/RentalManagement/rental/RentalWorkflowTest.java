package com.tamdaptit.rentalmanagement.rental;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamdaptit.rentalmanagement.common.exception.*;
import com.tamdaptit.rentalmanagement.rental.controller.*;
import com.tamdaptit.rentalmanagement.rental.dto.request.*;
import com.tamdaptit.rentalmanagement.rental.dto.response.*;
import com.tamdaptit.rentalmanagement.rental.repository.*;
import com.tamdaptit.rentalmanagement.rental.service.*;
import com.tamdaptit.rentalmanagement.room.repository.RoomRepository;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import jakarta.persistence.EntityManagerFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import javax.sql.DataSource;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/** Real MySQL 8 InnoDB; each service call is its own committed transaction. Never wraps tests in a transaction. */
@EnabledIfEnvironmentVariable(named="SUBSCRIPTION_TEST_MYSQL_URL",matches="jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/")
class RentalWorkflowTest {
    static String schema; static AnnotationConfigWebApplicationContext context; static JdbcTemplate jdbc;
    static RentalService service; static MockMvc mvc; static TestClock clock; static RentalPdfWorker worker;
    static FakeStorage storage;
    static String dbUser(){return System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_USER","root");}
    static String password(){return System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_PASSWORD","");}
    static String url(String db){return System.getenv("SUBSCRIPTION_TEST_MYSQL_URL")+db+"?serverTimezone=Asia/Ho_Chi_Minh&useSSL=false&allowPublicKeyRetrieval=true&sessionVariables=innodb_lock_wait_timeout=3";}
    @BeforeAll static void start() throws Exception {
        schema="rental_test_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(url(""),dbUser(),password());var s=c.createStatement()){s.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");}
        Flyway.configure().dataSource(url(schema),dbUser(),password()).target("1").load().migrate();
        try (var c=DriverManager.getConnection(url(schema),dbUser(),password())) {
            for (String migration : List.of("V6__notification_delivery.sql", "V7__role_permission_management.sql", "V8__nearby_places.sql"))
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,
                        new org.springframework.core.io.ClassPathResource("db/migration/"+migration));
        }
        context=new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();
        jdbc=context.getBean(JdbcTemplate.class);service=context.getBean(RentalService.class);clock=context.getBean(TestClock.class);
        storage=context.getBean(FakeStorage.class);worker=context.getBean(RentalPdfWorker.class);
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("INSERT INTO users(id,email,password,full_name,phone) VALUES ('owner','owner@rental.test','test','Chủ Trọ','0912345678'),('a','a@rental.test','test','Người A','0912345679'),('b','b@rental.test','test','Người B','0912345680'),('other','other@rental.test','test','Other','0912345681')");
        jdbc.update("INSERT INTO provinces(id,code,name) VALUES ('p','RENT-P','Hà Nội')");
        jdbc.update("INSERT INTO wards(id,province_id,code,name) VALUES ('w','p','RENT-W','Phường A')");
        jdbc.update("INSERT INTO room_types(id,name) VALUES ('rt','Phòng đơn')");
        jdbc.update("INSERT INTO property_types(id,name) VALUES ('pt-rental','Test thuê phòng')");
    }
    @BeforeEach void reset(){
        for(String t:List.of("notification_outbox","notifications","rental_events","rental_pdf_assets","rental_agreements","rental_requests","listings","rooms","properties"))jdbc.update("DELETE FROM "+t);
        clock.time=Instant.parse("2026-10-29T03:00:00Z");storage.bytes.clear();storage.fail=false;storage.uploads.set(0);
        jdbc.update("INSERT INTO properties(id,landlord_id,property_type_id,name,rental_mode,province_id,ward_id,address_line) VALUES ('prop','owner','pt-rental','Nhà trọ A','BY_ROOM','p','w','123 Đường A')");
        jdbc.update("INSERT INTO rooms(id,property_id,room_number,room_type_id,price,area_m2,max_occupants,default_deposit,electricity_price,water_price,service_fee) VALUES ('r','prop','A101','rt',3000000,25,2,1000000,3500,100000,50000),('r2','prop','A102','rt',3200000,30,3,1000000,3500,100000,50000)");
        jdbc.update("INSERT INTO listings(id,property_id,title,status,published_at,expires_at) VALUES ('l','prop','Tin cho thuê','PUBLISHED','2026-10-01','2027-12-31')");
    }
    @AfterAll static void close() throws Exception {
        if(context!=null)context.close();
        if(schema!=null&&schema.matches("rental_test_[a-f0-9]{32}"))try(var c=DriverManager.getConnection(url(""),dbUser(),password());var s=c.createStatement()){s.execute("DROP DATABASE `"+schema+"`");}
    }
    RentalRequestCreateRequest body(String room){return new RentalRequestCreateRequest("l",room,"Nguyễn Văn A","0912345678","tenant@example.test","012345678901","Hà Nội, Việt Nam",LocalDate.of(2026,11,15),12,2,"Tôi muốn thuê",true);}
    RentalRequestResponse create(String tenant){return service.create(tenant,body("r"));}
    @Test void tenantFeedFiltersAcrossPagesCountsOnlyOwnDataAndReturnsCardInformation() throws Exception {
        var first=create("a");
        var second=service.create("a",body("r2"));
        create("b");
        jdbc.update("UPDATE rental_requests SET created_at='2026-10-27 10:00:00' WHERE id=?",first.id());
        jdbc.update("UPDATE rental_requests SET created_at='2026-10-28 10:00:00' WHERE id=?",second.id());
        service.cancelRequest("a",second.id(),false,"Người thuê hủy yêu cầu");
        service.accept("owner",first.id());
        var summary=service.mySummary("a");
        assertEquals(2,summary.totalRequests()); assertEquals(1,summary.totalAgreements());
        assertEquals(1L,summary.statuses().get("ACCEPTED")); assertEquals(1L,summary.statuses().get("CANCELLED"));
        assertEquals(0L,summary.statuses().get("PENDING"));
        assertEquals(1,service.mySummary("b").totalRequests());
        var filtered=service.myRequests("a",0,1,com.tamdaptit.rentalmanagement.rental.entity.enums.RentalRequestStatus.CANCELLED,"Phường A","newest");
        assertEquals(1,filtered.getTotalElements()); assertEquals(second.id(),filtered.getData().getFirst().id());
        assertEquals("123 Đường A, Phường A, Hà Nội",filtered.getData().getFirst().card().fullAddress());
        assertEquals("Chủ Trọ",filtered.getData().getFirst().card().landlordName());
        assertNull(filtered.getData().getFirst().tenantPhone()); assertNull(filtered.getData().getFirst().roomDetails());
        assertEquals(first.id(),service.myRequests("a",0,1,null,"","oldest").getData().getFirst().id());
        assertEquals(0,service.myRequests("a",0,10,null,"%","newest").getTotalElements());
        var contracts=service.myAgreements("a",0,10,"Nhà trọ A","newest");
        assertEquals(1,contracts.getTotalElements()); assertNotNull(contracts.getData().getFirst().card());
        assertEquals(0,service.myAgreements("b",0,10,"","newest").getTotalElements());
        mvc.perform(get("/api/rental-requests/my").param("status","CANCELLED").param("q","A102").with(user("a").roles("TENANT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].card.price").value(3200000));
        mvc.perform(get("/api/rental-requests/my/summary").with(user("a").roles("TENANT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalRequests").value(2));
        assertThrows(AppException.class,()->service.myRequests("a",0,10,null,"","unsupported"));
    }
    @Test void requestDetailsRemainPrivateForHiddenListingsAndUseAgreedPrices() {
        var request=create("a");
        jdbc.update("UPDATE properties SET latitude=21.01,longitude=105.84 WHERE id='prop'");
        jdbc.update("UPDATE listings SET status='HIDDEN' WHERE id='l'");
        var detail=service.request("a",request.id(),false);
        assertEquals("123 Đường A, Phường A, Hà Nội",detail.roomDetails().fullAddress());
        assertEquals(0,new java.math.BigDecimal("21.01").compareTo(detail.roomDetails().latitude()));
        assertEquals("CURRENT_ROOM",detail.roomDetails().priceSource());
        assertEquals(0,new java.math.BigDecimal("3000000").compareTo(detail.roomDetails().price()));
        assertEquals("********8901",detail.maskedCitizenId());
        assertEquals(ErrorCode.NOT_AGREEMENT_PARTY,assertThrows(AppException.class,
                ()->service.request("other",request.id(),false)).getErrorCode());
        assertNull(service.requests("a",false,0,10,null,null,null).getData().getFirst().roomDetails());
        jdbc.update("UPDATE listings SET status='PUBLISHED' WHERE id='l'");
        service.accept("owner",request.id());
        jdbc.update("UPDATE rooms SET price=9900000 WHERE id='r'");
        var accepted=service.request("a",request.id(),false);
        assertEquals("AGREEMENT",accepted.roomDetails().priceSource());
        assertEquals(0,new java.math.BigDecimal("3000000").compareTo(accepted.roomDetails().price()));
    }
    RentalAgreementResponse accept(String tenant){return service.accept("owner",create(tenant).id());}
    String scalar(String sql){return jdbc.queryForObject(sql,String.class);}
    void parallel(Callable<?> first,Callable<?> second) throws Exception {
        var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{ready.countDown();assertTrue(go.await(10,TimeUnit.SECONDS));return first.call();});
            var b=pool.submit(()->{ready.countDown();assertTrue(go.await(10,TimeUnit.SECONDS));return second.call();});
            assertTrue(ready.await(10,TimeUnit.SECONDS));go.countDown();a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);
        }
    }
    @Test void twoTenantsCanSubmitAndPendingReplayChecksPayload() throws Exception {
        parallel(()->create("a"),()->create("b"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM rental_requests WHERE status='PENDING'",Integer.class));
        assertEquals("AVAILABLE",scalar("SELECT rental_status FROM rooms WHERE id='r'"));
        var r=create("a");assertEquals(r.id(),create("a").id());
        var different=new RentalRequestCreateRequest("l","r","Tên khác","0912345678","tenant@example.test","012345678901","Hà Nội, Việt Nam",LocalDate.of(2026,11,15),12,2,"Tôi muốn thuê",true);
        assertEquals(ErrorCode.REQUEST_ALREADY_PENDING,assertThrows(AppException.class,()->service.create("a",different)).getErrorCode());
        assertNotNull(service.create("a",body("r2"))); // same listing, another physical room is allowed
    }
    @Test void parallelAcceptHasExactlyOneWinnerAndDatabaseGuardRejectsBypass() throws Exception {
        var a=create("a");var b=create("b");AtomicInteger wins=new AtomicInteger();
        Callable<Void> first=()->{try{service.accept("owner",a.id());wins.incrementAndGet();}catch(AppException e){assertEquals(ErrorCode.REQUEST_NOT_PENDING,e.getErrorCode());}return null;};
        Callable<Void> second=()->{try{service.accept("owner",b.id());wins.incrementAndGet();}catch(AppException e){assertEquals(ErrorCode.REQUEST_NOT_PENDING,e.getErrorCode());}return null;};
        parallel(first,second);assertEquals(1,wins.get());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM rental_agreements",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM rental_requests WHERE status='ACCEPTED'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM rental_requests WHERE status='REJECTED'",Integer.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("""
            INSERT INTO rental_agreements(id,agreement_code,room_id,tenant_id,landlord_id,price,start_date,end_date,status,created_by_role)
            VALUES('bypass','bypass','r','b','owner',1,'2026-11-01','2027-11-01','DRAFT','LANDLORD')
            """));
        String request=scalar("SELECT id FROM rental_requests WHERE status='ACCEPTED'");
        assertEquals(service.accept("owner",request).id(),service.accept("owner",request).id());
    }
    @Test void submitVersusAcceptCannotLeavePendingOnHeldRoom() throws Exception {
        String id=create("a").id();
        parallel(()->service.accept("owner",id),()->{try{create("b");}catch(AppException e){assertEquals(ErrorCode.ROOM_UNAVAILABLE,e.getErrorCode());}return null;});
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM rental_requests WHERE status='PENDING'",Integer.class));
    }
    @Test void doubleSubmitAndDoubleAcceptAreIdempotent() throws Exception {
        Set<String> ids=ConcurrentHashMap.newKeySet();parallel(()->ids.add(create("a").id()),()->ids.add(create("a").id()));assertEquals(1,ids.size());
        Set<String> contracts=ConcurrentHashMap.newKeySet();String id=ids.iterator().next();
        parallel(()->contracts.add(service.accept("owner",id).id()),()->contracts.add(service.accept("owner",id).id()));assertEquals(1,contracts.size());
    }
    @Test void agreementResponsesExposePartyIdsFromCreationThroughCheckout() throws Exception {
        var agreement=accept("a");
        assertEquals("a",agreement.tenantId());assertEquals("owner",agreement.landlordId());
        mvc.perform(get("/api/rental-agreements/"+agreement.id()).with(user("a").roles("TENANT")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.tenantId").value("a"))
            .andExpect(jsonPath("$.data.landlordId").value("owner"));
        mvc.perform(get("/api/landlord/rental-agreements").with(user("owner").roles("LANDLORD")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.data[0].tenantId").value("a"))
            .andExpect(jsonPath("$.data.data[0].landlordId").value("owner"));
        worker.process(agreement.id());
        var ready=service.agreement("a",agreement.id(),false);
        var confirmed=service.confirm("a",agreement.id(),new RentalAgreementConfirmRequest(true,ready.pdfSha256()));
        assertEquals("a",confirmed.tenantId());assertEquals("owner",confirmed.landlordId());
        var checkout=service.checkout("owner",agreement.id(),new RentalCheckoutRequest(true,"Đã bàn giao"));
        assertEquals("a",checkout.tenantId());assertEquals("owner",checkout.landlordId());
        assertEquals("a",service.agreements("a",false,0,10).getData().getFirst().tenantId());
    }
    @Test void checkoutHistoryProtectsRoomBeforeAnyReviewExists() throws Exception {
        var agreement=accept("a");
        worker.process(agreement.id());
        var ready=service.agreement("a",agreement.id(),false);
        service.confirm("a",agreement.id(),new RentalAgreementConfirmRequest(true,ready.pdfSha256()));
        service.checkout("owner",agreement.id(),new RentalCheckoutRequest(true,"Đã bàn giao"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/rooms/r")
                .with(user("owner").roles("LANDLORD")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ROOM_HAS_RENTAL_HISTORY"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM rental_agreements",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM rooms",Integer.class));
    }
    @Test void pdfRetryUsesSnapshotAndConfirmRequiresTheDownloadedVersion() throws Exception {
        var a=accept("a");assertEquals("PENDING_CONFIRMATION",a.status());assertFalse(a.canConfirm());
        assertEquals(ErrorCode.CONTRACT_PDF_NOT_READY,assertThrows(AppException.class,()->service.confirm("a",a.id(),new RentalAgreementConfirmRequest(true,"a".repeat(64)))).getErrorCode());
        jdbc.update("UPDATE rooms SET price=9000000 WHERE id='r'");
        storage.fail=true;worker.process(a.id());assertEquals("FAILED",scalar("SELECT pdf_status FROM rental_agreements"));
        assertEquals("AVAILABLE",scalar("SELECT rental_status FROM rooms WHERE id='r'"));
        assertThrows(AppException.class,()->create("b"));
        clock.time=clock.time.plusSeconds(120);storage.fail=false;worker.process(a.id());
        var ready=service.agreement("a",a.id(),false);assertEquals("READY",ready.pdfStatus());assertTrue(ready.canConfirm());
        var ref=service.pdf("a",a.id(),false);byte[] pdf=storage.download(ref.publicId());assertEquals(ref.hash(),RentalContractPdfService.sha256(pdf));
        try(var doc=Loader.loadPDF(pdf)) {
            String text=new PDFTextStripper().getText(doc).replaceAll("\\s+"," ");
            for(String expected:List.of("HỢP ĐỒNG THUÊ PHÒNG TRỌ","Nguyễn Văn A","3.000.000 đồng/tháng","Ba triệu đồng","012345678901"))
                assertTrue(text.contains(expected),expected);
            assertFalse(text.contains("9.000.000"));
        }
        assertEquals(ErrorCode.CONTRACT_CHANGED,assertThrows(AppException.class,()->service.confirm("a",a.id(),new RentalAgreementConfirmRequest(true,"0".repeat(64)))).getErrorCode());
        service.confirm("a",a.id(),new RentalAgreementConfirmRequest(true,ref.hash()));
        var again=service.confirm("a",a.id(),new RentalAgreementConfirmRequest(true,ref.hash()));assertEquals("ACTIVE",again.status());
        assertArrayEquals(pdf,storage.download(ref.publicId()));assertEquals("RENTED",scalar("SELECT rental_status FROM rooms WHERE id='r'"));
        context.getBean(RentalEvents.class).deliver();context.getBean(RentalEvents.class).deliver();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM rental_events WHERE delivered_at IS NULL",Integer.class));
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM rental_events",Integer.class),jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox",Integer.class));
    }
    @Test void confirmationVersusTimeoutAtExactBoundaryNeverActivates() throws Exception {
        var a=accept("a");worker.process(a.id());String hash=service.agreement("a",a.id(),false).pdfSha256();
        clock.time=clock.time.plus(Duration.ofHours(48));
        parallel(()->{assertTrue(Set.of(ErrorCode.CONTRACT_CONFIRMATION_EXPIRED,ErrorCode.INVALID_AGREEMENT_STATUS).contains(assertThrows(AppException.class,()->service.confirm("a",a.id(),new RentalAgreementConfirmRequest(true,hash))).getErrorCode()));return null;},
            ()->{service.expireAgreement(a.id());return null;});
        assertEquals("CANCELLED",scalar("SELECT status FROM rental_agreements"));assertEquals("AVAILABLE",scalar("SELECT rental_status FROM rooms WHERE id='r'"));
        assertNotNull(create("b"));
    }
    @Test void confirmationVersusSchedulerBeforeDeadlineStaysConsistent() throws Exception {
        var a=accept("a");worker.process(a.id());String hash=service.agreement("a",a.id(),false).pdfSha256();
        clock.time=clock.time.plus(Duration.ofHours(47));
        parallel(()->service.confirm("a",a.id(),new RentalAgreementConfirmRequest(true,hash)),()->{service.expireAgreement(a.id());return null;});
        assertEquals("ACTIVE",scalar("SELECT status FROM rental_agreements"));assertEquals("RENTED",scalar("SELECT rental_status FROM rooms WHERE id='r'"));
    }
    @Test void leaseRecoveryAndStaleWorkerCannotOverwriteReadyPdf() throws Exception {
        var a=accept("a");var jobs=context.getBean(RentalPdfJobs.class);var claim=jobs.claim(a.id());assertNotNull(claim);assertNull(jobs.claim(a.id()));
        clock.time=clock.time.plusSeconds(301);worker.process(a.id());assertEquals("READY",service.agreement("a",a.id(),false).pdfStatus());
        assertFalse(jobs.complete(claim,"a".repeat(64)));assertEquals(1,storage.uploads.get());
        // Exhausted crashed workers become FAILED instead of retrying forever.
        jdbc.update("UPDATE rental_agreements SET pdf_status='PROCESSING',pdf_attempt_count=6,pdf_lease_until=?",LocalDateTime.now(clock).minusMinutes(1));
        assertNull(jobs.claim(a.id()));assertEquals("FAILED",scalar("SELECT pdf_status FROM rental_agreements"));
    }
    @Test void releaseCheckoutAndDifferentRoomsKeepHistory() {
        var a=accept("a");service.decline("a",a.id());assertEquals("ACCEPTED",scalar("SELECT status FROM rental_requests"));
        assertNotNull(create("b"));
        var other=service.accept("owner",service.create("a",body("r2")).id());worker.process(other.id());
        service.confirm("a",other.id(),new RentalAgreementConfirmRequest(true,service.agreement("a",other.id(),false).pdfSha256()));
        var b=service.accept("owner",scalar("SELECT id FROM rental_requests WHERE tenant_id='b'"));worker.process(b.id());
        service.confirm("b",b.id(),new RentalAgreementConfirmRequest(true,service.agreement("b",b.id(),false).pdfSha256()));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM rental_agreements WHERE status='ACTIVE'",Integer.class));
        service.checkout("owner",b.id(),new RentalCheckoutRequest(true,"Đã bàn giao"));assertEquals("AVAILABLE",scalar("SELECT rental_status FROM rooms WHERE id='r'"));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM rental_agreements WHERE contract_snapshot IS NOT NULL",Integer.class));
    }
    @Test void wholeMappingForeignRoomPrivacyAndPermissions() throws Exception {
        jdbc.update("UPDATE properties SET rental_mode='WHOLE' WHERE id='prop'");
        assertThrows(AppException.class,()->create("a"));jdbc.update("UPDATE properties SET rental_mode='BY_ROOM' WHERE id='prop'");
        jdbc.update("INSERT INTO properties(id,landlord_id,property_type_id,name,rental_mode,province_id,ward_id,address_line) SELECT 'foreign','owner',property_type_id,'Foreign','BY_ROOM',province_id,ward_id,address_line FROM properties WHERE id='prop'");
        jdbc.update("UPDATE rooms SET property_id='foreign' WHERE id='r2'");assertThrows(AppException.class,()->service.create("a",body("r2")));
        assertThrows(AppException.class,()->service.create("owner",body("r")));
        var r=create("a");assertThrows(AppException.class,()->service.accept("other",r.id()));
        var a=service.accept("owner",r.id());worker.process(a.id());
        mvc.perform(get("/api/rental-agreements/"+a.id()+"/pdf").with(user("b").roles("TENANT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/rental-agreements/"+a.id()+"/pdf").with(user("a").roles("TENANT"))).andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(post("/api/rental-agreements/"+a.id()+"/confirm").with(user("owner").roles("LANDLORD")).contentType(APPLICATION_JSON).content("{\"accepted\":true,\"expectedPdfSha256\":\""+service.agreement("a",a.id(),false).pdfSha256()+"\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/rental-requests/my").with(user("a").roles("TENANT"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.data[0].tenantCitizenId").doesNotExist()).andExpect(jsonPath("$.data.data[0].permanentAddress").doesNotExist());
        mvc.perform(post("/api/rental-requests").with(user("a").roles("TENANT")).contentType(APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/rental-requests").with(user("a").roles("TENANT")).contentType(APPLICATION_JSON).content("{\"durationMonths\":1.5}"))
            .andExpect(status().isBadRequest());
    }
    @Test void databaseLockTimeoutIsConflictAndManualStatusCannotBypassHold() throws Exception {
        var a=accept("a");
        mvc.perform(get("/api/rooms/r").with(user("owner").roles("LANDLORD")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.bookingAvailability").value("HELD"))
            .andExpect(jsonPath("$.data.rentalStatus").value("AVAILABLE"));
        // Exercise the existing landlord room API under the same physical-room lock and guard.
        mvc.perform(put("/api/rooms/r").with(user("owner").roles("LANDLORD")).contentType(APPLICATION_JSON).content("""
            {"roomTypeId":"rt","price":3000000,"areaM2":25,"maxOccupants":2,"propertyId":"prop","roomNumber":"A101","rentalStatus":"MAINTENANCE"}
            """)).andExpect(status().isConflict());
        assertEquals("PENDING_CONFIRMATION",service.agreement("a",a.id(),false).status());
        try(var connection=DriverManager.getConnection(url(schema),dbUser(),password())) {
            connection.setAutoCommit(false);
            try(var statement=connection.createStatement()) {
                statement.executeQuery("SELECT id FROM rooms WHERE id='r' FOR UPDATE").close();
                mvc.perform(post("/api/landlord/rental-requests/"+a.requestId()+"/accept").with(user("owner").roles("LANDLORD")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ROOM_BUSY"));
            } finally { connection.rollback(); }
        }
    }
    static class TestClock extends Clock {
        volatile Instant time=Instant.parse("2026-10-29T03:00:00Z");public Instant instant(){return time;}
        public ZoneId getZone(){return ZoneId.of("Asia/Ho_Chi_Minh");}public Clock withZone(ZoneId z){return Clock.fixed(time,z);}
    }
    static class FakeStorage implements RentalPdfStorage {
        final Map<String,byte[]> bytes=new ConcurrentHashMap<>();boolean fail;AtomicInteger uploads=new AtomicInteger();
        public void upload(String id,byte[] value) throws Exception {if(fail)throw new java.io.IOException("simulated storage failure");bytes.put(id,value);uploads.incrementAndGet();}
        public byte[] download(String id){return bytes.get(id);}public void delete(String id){bytes.remove(id);}
    }
    @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses={RentalRequestRepository.class,RentalAgreementRepository.class,RoomRepository.class,UserRepository.class,ListingRepository.class,com.tamdaptit.rentalmanagement.property.repository.PropertyRepository.class})
    @Import({com.tamdaptit.rentalmanagement.rental.service.impl.RentalContractServiceImpl.class,com.tamdaptit.rentalmanagement.notification.service.NotificationPublisher.class,com.tamdaptit.rentalmanagement.rental.service.impl.RentalServiceImpl.class,RentalAvailabilityService.class,RentalEvents.class,RentalPdfJobs.class,RentalPdfWorker.class,RentalContractPdfService.class,
        RentalRequestController.class,RentalAgreementController.class,GlobalExceptionHandler.class,
        com.tamdaptit.rentalmanagement.room.controller.RoomController.class,com.tamdaptit.rentalmanagement.room.service.impl.RoomServiceImpl.class})
    static class Config {
        @Bean SecurityFilterChain security(HttpSecurity http)throws Exception{return http.csrf(c->c.disable()).authorizeHttpRequests(a->a.anyRequest().authenticated()).build();}
        @Bean TestClock clock(){return new TestClock();}
        @Bean ObjectMapper mapper(){return new ObjectMapper();}
        @Bean FakeStorage storage(){return new FakeStorage();}
        @Bean com.tamdaptit.rentalmanagement.storage.service.ImageStorageService images(){return org.mockito.Mockito.mock(com.tamdaptit.rentalmanagement.storage.service.ImageStorageService.class);}
        @Bean com.tamdaptit.rentalmanagement.room.mapper.RoomMapper roomsMapper(){return org.mapstruct.factory.Mappers.getMapper(com.tamdaptit.rentalmanagement.room.mapper.RoomMapper.class);}
        @Bean DataSource dataSource(){return new DriverManagerDataSource(url(schema),dbUser(),password());}
        @Bean JdbcTemplate jdbc(DataSource ds){return new JdbcTemplate(ds);}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){
            var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("com.tamdaptit.rentalmanagement");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate"));return f;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory f){return new JpaTransactionManager(f);}
    }
}
