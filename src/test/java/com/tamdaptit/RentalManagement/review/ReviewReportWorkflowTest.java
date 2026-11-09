package com.tamdaptit.rentalmanagement.review;

import com.tamdaptit.rentalmanagement.auth.service.JwtTokenService;
import com.tamdaptit.rentalmanagement.common.config.*;
import com.tamdaptit.rentalmanagement.common.exception.*;
import com.tamdaptit.rentalmanagement.common.security.JwtAuthenticationFilter;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.notification.repository.NotificationRepository;
import com.tamdaptit.rentalmanagement.report.controller.ReportController;
import com.tamdaptit.rentalmanagement.report.dto.request.*;
import com.tamdaptit.rentalmanagement.report.entity.enums.*;
import com.tamdaptit.rentalmanagement.report.mapper.ReportMapper;
import com.tamdaptit.rentalmanagement.report.repository.ReportRepository;
import com.tamdaptit.rentalmanagement.report.service.*;
import com.tamdaptit.rentalmanagement.report.service.impl.ReportServiceImpl;
import com.tamdaptit.rentalmanagement.review.controller.ReviewController;
import com.tamdaptit.rentalmanagement.review.dto.request.*;
import com.tamdaptit.rentalmanagement.review.entity.enums.ReviewType;
import com.tamdaptit.rentalmanagement.review.mapper.ReviewMapper;
import com.tamdaptit.rentalmanagement.review.repository.RoomReviewRepository;
import com.tamdaptit.rentalmanagement.review.service.ReviewService;
import com.tamdaptit.rentalmanagement.review.service.impl.ReviewServiceImpl;
import com.tamdaptit.rentalmanagement.storage.service.ImageValidator;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.*;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import javax.sql.DataSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
import static com.tamdaptit.rentalmanagement.common.exception.ErrorCode.*;

/** MySQL in a random disposable schema; uses the production security filter chain. */
@EnabledIfEnvironmentVariable(named="SUBSCRIPTION_TEST_MYSQL_URL",matches="jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/")
class ReviewReportWorkflowTest {
    static String schema;
    static AnnotationConfigWebApplicationContext context;
    static JdbcTemplate jdbc;
    static ReviewService reviews;
    static ReportService reports;
    static ReportEvidenceService evidences;
    static FakeStorage storage;
    static MockMvc mvc;
    static String dbUser(){return System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_USER","root");}
    static String password(){return System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_PASSWORD","");}
    static String url(String db){return System.getenv("SUBSCRIPTION_TEST_MYSQL_URL")+db+"?serverTimezone=Asia/Ho_Chi_Minh&useSSL=false&allowPublicKeyRetrieval=true&sessionVariables=innodb_lock_wait_timeout=5";}

    @BeforeAll static void start() throws Exception {
        schema="review_report_test_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(url(""),dbUser(),password());var s=c.createStatement()){
            s.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");
        }
        Flyway.configure().dataSource(url(schema),dbUser(),password()).target("1").load().migrate();
        context=new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();
        jdbc=context.getBean(JdbcTemplate.class);reviews=context.getBean(ReviewService.class);
        reports=context.getBean(ReportService.class);evidences=context.getBean(ReportEvidenceService.class);
        storage=context.getBean(FakeStorage.class);
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("INSERT INTO roles(id,code,name) VALUES('rr_admin_role','ADMIN','Admin'),('rr_tenant_role','TENANT','Tenant'),('rr_landlord_role','LANDLORD','Landlord')");
        for(String id:List.of("rr_t","rr_u","rr_l","rr_m","rr_n","rr_a","rr_b")) {
            jdbc.update("INSERT INTO users(id,email,password,full_name) VALUES(?,?,?,?)",id,id+"@review.test","test","Name "+id);
            String role=id.equals("rr_a")||id.equals("rr_b")?"ADMIN":id.equals("rr_t")||id.equals("rr_u")?"TENANT":"LANDLORD";
            jdbc.update("INSERT INTO user_roles(id,user_id,role_id) SELECT ?,?,id FROM roles WHERE code=?",UUID.randomUUID().toString(),id,role);
        }
        jdbc.update("INSERT INTO provinces(id,code,name) VALUES('rr_p','RR-P','Test')");
        jdbc.update("INSERT INTO wards(id,province_id,code,name) VALUES('rr_w','rr_p','RR-W','Test')");
        jdbc.update("INSERT INTO room_types(id,name) VALUES('rr_rt','Test')");
        jdbc.update("INSERT INTO property_types(id,name) VALUES('rr_pt','Test')");
    }

    @BeforeEach void reset() {
        for(String table:List.of("report_evidences","reports","room_reviews","landlord_reviews","tenant_reviews",
            "notification_outbox","notifications","rental_events","rental_pdf_assets","rental_agreements","rental_requests","listings","rooms","properties")) {
            jdbc.update("DELETE FROM "+table);
        }
        jdbc.update("UPDATE users SET status='ACTIVE' WHERE id LIKE 'rr_%'");
        storage.assets.clear();storage.fail=false;storage.onUpload=()->{};
        jdbc.update("""
            INSERT INTO properties(id,landlord_id,property_type_id,name,rental_mode,province_id,ward_id,address_line)
            VALUES('rr_p1','rr_l','rr_pt','Test P','BY_ROOM','rr_p','rr_w','Test Address'),
                  ('rr_p2','rr_m','rr_pt','Test Q','BY_ROOM','rr_p','rr_w','Test Address')
            """);
        jdbc.update("""
            INSERT INTO rooms(id,property_id,room_number,room_type_id,price,area_m2,max_occupants)
            VALUES('rr_r1','rr_p1','101','rr_rt',3000000,25,2),('rr_r2','rr_p1','102','rr_rt',3000000,25,2),
                  ('rr_r3','rr_p2','201','rr_rt',3000000,25,2),('rr_r4','rr_p1','103','rr_rt',3000000,25,2)
            """);
        jdbc.update("""
            INSERT INTO listings(id,property_id,title,status,published_at,expires_at)
            VALUES('rr_listing','rr_p1','Test Listing','PUBLISHED','2026-10-01','2028-01-01'),
                  ('rr_listing2','rr_p2','Other Listing','PUBLISHED','2026-10-01','2028-01-01')
            """);
        agreement("rr_active","rr_r1","rr_t","rr_l","ACTIVE");
        agreement("rr_ended","rr_r2","rr_u","rr_l","EXPIRED");
        agreement("rr_history","rr_r3","rr_t","rr_m","TERMINATED");
    }
    void agreement(String id,String room,String tenant,String landlord,String status) {
        jdbc.update("""
            INSERT INTO rental_agreements(id,agreement_code,room_id,tenant_id,landlord_id,price,start_date,end_date,status,created_by_role,landlord_confirmed_at,tenant_confirmed_at)
            VALUES(?,?,?,?,?,3000000,'2026-10-01','2027-10-01',?,'LANDLORD','2026-09-30','2026-09-30')
            """,id,id,room,tenant,landlord,status);
    }
    @AfterAll static void close() throws Exception {
        if(context!=null)context.close();
        if(schema!=null && schema.matches("review_report_test_[a-f0-9]{32}"))
            try(var c=DriverManager.getConnection(url(""),dbUser(),password());var s=c.createStatement()){s.execute("DROP DATABASE `"+schema+"`");}
    }
    ReviewCreateRequest review(String agreement,int rating){return new ReviewCreateRequest(agreement,rating,"  Nội dung  ");}
    ReportCreateRequest report(ReportTargetType type,String id){return new ReportCreateRequest(type,id,ReportReason.SCAM,"Test");}
    String listingReport(){return reports.create("rr_t",report(ReportTargetType.LISTING,"rr_listing")).id();}
    void error(ErrorCode code,org.junit.jupiter.api.function.Executable action) {
        assertEquals(code,assertThrows(AppException.class,action).getErrorCode());
    }
    List<Object> parallel(Callable<?> a,Callable<?> b) throws Exception {
        var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Object> first=()->{ready.countDown();assertTrue(go.await(10,TimeUnit.SECONDS));return a.call();};
            Callable<Object> second=()->{ready.countDown();assertTrue(go.await(10,TimeUnit.SECONDS));return b.call();};
            var fa=pool.submit(first);var fb=pool.submit(second);
            assertTrue(ready.await(10,TimeUnit.SECONDS));go.countDown();
            return List.of(fa.get(30,TimeUnit.SECONDS),fb.get(30,TimeUnit.SECONDS));
        }
    }

    @Test void createsIndependentImmutableReviewsDuringAndAfterRental() {
        var room=reviews.create("rr_t",ReviewType.ROOM,review("rr_active",5));
        assertEquals("Nội dung",room.comment());
        assertNotNull(reviews.create("rr_t",ReviewType.LANDLORD,review("rr_active",4)).id());
        assertNotNull(reviews.create("rr_l",ReviewType.TENANT,review("rr_active",3)).id());
        assertNotNull(reviews.create("rr_u",ReviewType.ROOM,review("rr_ended",2)).id());
        assertNotNull(reviews.create("rr_t",ReviewType.ROOM,review("rr_history",4)).id());
        error(ROOM_REVIEW_EXISTED,()->reviews.create("rr_t",ReviewType.ROOM,review("rr_active",1)));
        assertEquals(room.id(),reviews.eligibility("rr_t","rr_active").room().reviewId());
        assertNull(reviews.eligibility("rr_l","rr_active").room().reviewId());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM room_reviews WHERE is_hidden IS NULL",Integer.class));
    }
    @Test void rejectsWrongPartyRoleFutureCancelledAndUnconfirmedContracts() {
        error(NOT_AGREEMENT_PARTY,()->reviews.create("rr_u",ReviewType.ROOM,review("rr_active",4)));
        error(UNAUTHORIZED,()->reviews.create("rr_t",ReviewType.TENANT,review("rr_active",4)));
        for(String state:List.of("DRAFT","PENDING_CONFIRMATION","CANCELLED")) {
            jdbc.update("UPDATE rental_agreements SET status=? WHERE id='rr_active'",state);
            error(REVIEW_NOT_ELIGIBLE,()->reviews.create("rr_t",ReviewType.ROOM,review("rr_active",4)));
        }
        jdbc.update("UPDATE rental_agreements SET status='ACTIVE',start_date='2027-01-01' WHERE id='rr_active'");
        error(REVIEW_NOT_ELIGIBLE,()->reviews.create("rr_t",ReviewType.ROOM,review("rr_active",4)));
        jdbc.update("UPDATE rental_agreements SET start_date='2026-10-01',tenant_confirmed_at=NULL WHERE id='rr_active'");
        error(REVIEW_NOT_ELIGIBLE,()->reviews.create("rr_t",ReviewType.ROOM,review("rr_active",4)));
        error(INVALID_RATING,()->reviews.create("rr_t",ReviewType.ROOM,review("rr_history",0)));
    }
    @Test void concurrentReviewCreationHasOneWinner() throws Exception {
        Callable<Object> create=()->{try {reviews.create("rr_t",ReviewType.ROOM,review("rr_active",5));return "OK";}
            catch(AppException ex){return ex.getErrorCode();}};
        var results=parallel(create,create);
        assertEquals(1,Collections.frequency(results,"OK"));
        assertEquals(1,Collections.frequency(results,ROOM_REVIEW_EXISTED));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM room_reviews",Integer.class));
    }
    @Test void moderationChangesAggregateAndPreservesUsedSlotAndPrivateMetadata() {
        var first=reviews.create("rr_t",ReviewType.ROOM,review("rr_active",5));
        reviews.create("rr_u",ReviewType.ROOM,review("rr_ended",1));
        var sum=reviews.listingSummary("rr_listing",null);
        assertEquals(2,sum.totalReviews());assertEquals("3.0",sum.averageRating().toString());
        reviews.visibility("rr_a",ReviewType.ROOM,first.id(),new ReviewVisibilityRequest(true,"Spam"));
        assertEquals(1,reviews.listingSummary("rr_listing",null).totalReviews());
        assertEquals("1.0",reviews.listingSummary("rr_listing",null).averageRating().toString());
        assertTrue(reviews.mine("rr_t",ReviewType.ROOM,0,10).getData().getFirst().hidden());
        assertEquals("Spam",reviews.mine("rr_t",ReviewType.ROOM,0,10).getData().getFirst().hiddenReason());
        error(ROOM_REVIEW_EXISTED,()->reviews.create("rr_t",ReviewType.ROOM,review("rr_active",2)));
        error(REVIEW_HIDDEN,()->reviews.reply("rr_l",ReviewType.ROOM,first.id(),new ReviewReplyRequest("Hello")));
        var visible=reviews.visibility("rr_a",ReviewType.ROOM,first.id(),new ReviewVisibilityRequest(false,null));
        assertNull(visible.hiddenAt());assertNull(visible.hiddenBy());assertNull(visible.hiddenReason());
        assertEquals(2,reviews.listingSummary("rr_listing",null).totalReviews());
        error(INVALID_REQUEST,()->reviews.visibility("rr_a",ReviewType.ROOM,first.id(),new ReviewVisibilityRequest(true," ")));
        error(UNAUTHORIZED,()->reviews.visibility("rr_l",ReviewType.ROOM,first.id(),new ReviewVisibilityRequest(true,"Spam")));
    }
    @Test void landlordReplyUsesAgreementOwnerAndHiddenReviewsStayPrivate() {
        var review=reviews.create("rr_t",ReviewType.ROOM,review("rr_active",4));
        error(UNAUTHORIZED,()->reviews.reply("rr_m",ReviewType.ROOM,review.id(),new ReviewReplyRequest("Reply")));
        jdbc.update("UPDATE properties SET landlord_id='rr_m' WHERE id='rr_p1'");
        error(UNAUTHORIZED,()->reviews.reply("rr_m",ReviewType.ROOM,review.id(),new ReviewReplyRequest("Reply")));
        assertEquals("Reply",reviews.reply("rr_l",ReviewType.ROOM,review.id(),new ReviewReplyRequest("Reply")).landlordReply());
    }
    @Test void relatedLandlordSeesOtherLandlordsTenantReviewsButNotHiddenOnes() {
        var first=reviews.create("rr_l",ReviewType.TENANT,review("rr_active",5));
        reviews.create("rr_m",ReviewType.TENANT,review("rr_history",1));
        assertEquals(2,reviews.tenant("rr_m","rr_t",0,10).getTotalElements());
        assertEquals(2,reviews.tenant("rr_t","rr_t",0,10).getTotalElements());
        assertEquals(2,reviews.tenant("rr_a","rr_t",0,10).getTotalElements());
        error(UNAUTHORIZED,()->reviews.tenant("rr_n","rr_t",0,10));
        error(UNAUTHORIZED,()->reviews.tenantSummary("rr_u","rr_t"));
        reviews.visibility("rr_a",ReviewType.TENANT,first.id(),new ReviewVisibilityRequest(true,"Hidden"));
        assertEquals(1,reviews.tenant("rr_m","rr_t",0,10).getTotalElements());
        assertEquals(1,reviews.tenantSummary("rr_m","rr_t").totalReviews());
        assertEquals(2,reviews.admin("rr_a",ReviewType.TENANT,null,"rr_t",0,10).getTotalElements());
    }
    @Test void publicQueriesRespectListingScopeLatestListingAndEmptySummary() {
        assertNull(reviews.listingSummary("rr_listing",null).averageRating());
        error(ROOM_NOT_FOUND,()->reviews.listing("rr_listing","rr_r3",0,10));
        error(INVALID_REQUEST,()->reviews.listing("rr_listing",null,0,51));
        jdbc.update("UPDATE listings SET status='HIDDEN' WHERE id='rr_listing'");
        error(LISTING_NOT_FOUND,()->reviews.listingSummary("rr_listing",null));
        error(USER_NOT_FOUND,()->reviews.landlord("rr_l",0,10));
        jdbc.update("UPDATE listings SET status='PUBLISHED',published_at='2025-01-01',expires_at='2026-01-01' WHERE id='rr_listing'");
        error(LISTING_NOT_FOUND,()->reviews.listingSummary("rr_listing",null));
    }
    @Test void propertyAverageWeightsEveryReviewAndRejectsSupersededListing() {
        agreement("rr_extra","rr_r1","rr_u","rr_l","EXPIRED");
        reviews.create("rr_t",ReviewType.ROOM,review("rr_active",5));
        reviews.create("rr_u",ReviewType.ROOM,review("rr_extra",5));
        reviews.create("rr_u",ReviewType.ROOM,review("rr_ended",1));
        assertEquals("3.7",reviews.listingSummary("rr_listing",null).averageRating().toString());
        assertEquals("5.0",reviews.listingSummary("rr_listing","rr_r1").averageRating().toString());
        assertEquals(3,reviews.listing("rr_listing",null,0,10).getTotalElements());
        assertEquals(0,reviews.listingSummary("rr_listing2",null).totalReviews());
        jdbc.update("INSERT INTO listings(id,property_id,title,status,published_at,expires_at) VALUES('rr_new','rr_p1','New','PUBLISHED','2026-11-01','2028-01-01')");
        error(LISTING_NOT_FOUND,()->reviews.listingSummary("rr_listing",null));
        assertEquals(3,reviews.listingSummary("rr_new",null).totalReviews());
    }
    @Test void dualRoleAccountCannotReportItself() {
        jdbc.update("INSERT INTO user_roles(id,user_id,role_id) SELECT 'rr_extra_role','rr_l',id FROM roles WHERE code='TENANT'");
        try {
            error(CANNOT_REPORT_SELF,()->reports.create("rr_l",report(ReportTargetType.LISTING,"rr_listing")));
            error(CANNOT_REPORT_SELF,()->reports.create("rr_l",report(ReportTargetType.LANDLORD,"rr_l")));
        } finally {jdbc.update("DELETE FROM user_roles WHERE id='rr_extra_role'");}
    }
    @Test void reportCreationDerivesOwnerAndRequiresConfirmedLandlordRelationship() {
        var report=reports.create("rr_t",report(ReportTargetType.LISTING,"rr_listing"));
        assertEquals("rr_l",report.landlordId());assertEquals(ReportStatus.PENDING,report.status());
        error(REPORT_ALREADY_OPEN,()->listingReport());
        assertNotNull(reports.create("rr_t",report(ReportTargetType.LANDLORD,"rr_l")).id());
        assertNotNull(reports.create("rr_t",report(ReportTargetType.LANDLORD,"rr_m")).id());
        error(REPORT_CONTRACT_REQUIRED,()->reports.create("rr_t",report(ReportTargetType.LANDLORD,"rr_n")));
        jdbc.update("UPDATE rental_agreements SET tenant_confirmed_at=NULL WHERE id='rr_history'");
        error(REPORT_CONTRACT_REQUIRED,()->reports.create("rr_t",report(ReportTargetType.LANDLORD,"rr_m")));
        error(INVALID_REQUEST,()->reports.create("rr_u",new ReportCreateRequest(ReportTargetType.LISTING,"rr_listing",ReportReason.OTHER," ")));
        error(INVALID_REQUEST,()->reports.create("rr_u",new ReportCreateRequest(ReportTargetType.LANDLORD,"rr_l",ReportReason.INVALID_PRICE,"Bad")));
        error(UNAUTHORIZED,()->reports.create("rr_l",report(ReportTargetType.LISTING,"rr_listing")));
    }
    @Test void concurrentReportCreationSerializesByReporter() throws Exception {
        Callable<Object> create=()->{try{return listingReport().isBlank()?"BAD":"OK";}catch(AppException ex){return ex.getErrorCode();}};
        var result=parallel(create,create);
        assertEquals(1,Collections.frequency(result,"OK"));
        assertEquals(1,Collections.frequency(result,REPORT_ALREADY_OPEN));
    }
    @Test void workflowKeepsAssignmentOwnershipNotesNotificationsAndListingUnchanged() {
        String id=listingReport();
        error(REPORT_INVALID_TRANSITION,()->reports.close("rr_a",id,ReportStatus.RESOLVED,new ReportResolutionRequest("Done")));
        assertEquals(ReportStatus.PROCESSING,reports.assign("rr_a",id).status());
        error(REPORT_NOT_ASSIGNED_TO_YOU,()->reports.close("rr_b",id,ReportStatus.RESOLVED,new ReportResolutionRequest("Done")));
        error(INVALID_REQUEST,()->reports.close("rr_a",id,ReportStatus.RESOLVED,new ReportResolutionRequest(" ")));
        var closed=reports.close("rr_a",id,ReportStatus.RESOLVED,new ReportResolutionRequest("Verified"));
        assertNotNull(closed.resolvedAt());assertEquals("rr_a",closed.handledBy());assertEquals("Verified",closed.resolutionNote());
        error(REPORT_INVALID_TRANSITION,()->reports.assign("rr_a",id));
        error(REPORT_INVALID_TRANSITION,()->reports.close("rr_a",id,ReportStatus.REJECTED,new ReportResolutionRequest("Again")));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE reference_id=?",Integer.class,id));
        assertEquals("PUBLISHED",jdbc.queryForObject("SELECT status FROM listings WHERE id='rr_listing'",String.class));
        assertNotEquals(id,listingReport());
        assertEquals(1,reports.summary("rr_a").get(ReportStatus.RESOLVED));
    }
    @Test void competingAdminsHaveOneAssignmentWinner() throws Exception {
        String id=listingReport();
        var result=parallel(()->assign("rr_a",id),()->assign("rr_b",id));
        assertEquals(1,Collections.frequency(result,"OK"));
        assertEquals(1,Collections.frequency(result,REPORT_INVALID_TRANSITION));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE reference_id=?",Integer.class,id));
    }
    Object assign(String actor,String id){try{reports.assign(actor,id);return "OK";}catch(AppException ex){return ex.getErrorCode();}}
    @Test void concurrentClosingCreatesOnlyOneOutcomeAndOneNotification() throws Exception {
        String id=listingReport();reports.assign("rr_a",id);
        Callable<Object> close=()->{
            try {reports.close("rr_a",id,ReportStatus.RESOLVED,new ReportResolutionRequest("Done"));return "OK";}
            catch(AppException ex){return ex.getErrorCode();}
        };
        var result=parallel(close,close);
        assertEquals(1,Collections.frequency(result,"OK"));
        assertEquals(1,Collections.frequency(result,REPORT_INVALID_TRANSITION));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE reference_id=?",Integer.class,id));
    }
    @Test void notificationOutboxInsertFailureRollsBackAssignment() {
        String id=listingReport();
        jdbc.execute("CREATE TRIGGER rr_fail_notification BEFORE INSERT ON notification_outbox FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Simulated notification outbox failure'");
        try {
            assertThrows(org.springframework.dao.DataAccessException.class,()->reports.assign("rr_a",id));
            var report=reports.detail("rr_t",id).report();
            assertEquals(ReportStatus.PENDING,report.status());assertNull(report.handledBy());
        } finally {jdbc.execute("DROP TRIGGER rr_fail_notification");}
    }

    MockMultipartFile image() throws Exception {
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);
        return new MockMultipartFile("file","proof.png","image/png",out.toByteArray());
    }
    @Test void evidenceIsPrivateAndChecksBothReportAndEvidenceOwnership() throws Exception {
        String id=listingReport();var image=image();
        var proof=evidences.upload("rr_t",id,image);
        assertTrue(proof.contentUrl().endsWith(proof.id()+"/content"));
        assertArrayEquals(image.getBytes(),evidences.download("rr_t",id,proof.id()).bytes());
        assertArrayEquals(image.getBytes(),evidences.download("rr_a",id,proof.id()).bytes());
        error(UNAUTHORIZED,()->evidences.download("rr_u",id,proof.id()));
        error(UNAUTHORIZED,()->reports.detail("rr_l",id));
        String other=reports.create("rr_u",report(ReportTargetType.LISTING,"rr_listing")).id();
        error(REPORT_EVIDENCE_NOT_FOUND,()->evidences.download("rr_u",other,proof.id()));
        reports.assign("rr_a",id);
        error(REPORT_NOT_PENDING,()->evidences.upload("rr_t",id,image));
        jdbc.update("UPDATE listings SET status='HIDDEN' WHERE id='rr_listing'");
        assertEquals(id,reports.detail("rr_t",id).report().id());
    }
    @Test void validatesFilesAndEnforcesEvidenceLimitUnderConcurrentUploads() throws Exception {
        String id=listingReport();var image=image();
        error(IMAGE_INVALID,()->evidences.upload("rr_t",id,new MockMultipartFile("file","bad.png","image/png",new byte[]{1,2,3})));
        error(IMAGE_INVALID,()->evidences.upload("rr_t",id,new MockMultipartFile("file","bad.jpg","image/jpeg",image.getBytes())));
        error(IMAGE_INVALID,()->evidences.upload("rr_t",id,new MockMultipartFile("file","big.png","image/png",new byte[5*1024*1024+1])));
        for(int i=0;i<4;i++)evidences.upload("rr_t",id,image);
        Callable<Object> upload=()->{try{evidences.upload("rr_t",id,image);return "OK";}catch(AppException ex){return ex.getErrorCode();}};
        var result=parallel(upload,upload);
        assertEquals(1,Collections.frequency(result,"OK"));assertEquals(1,Collections.frequency(result,IMAGE_TOO_MANY));
        assertEquals(5,storage.assets.size());
    }
    @Test void cleansAssetsWhenUploadFailsOrReportChangesDuringNetworkCall() throws Exception {
        String id=listingReport();var image=image();
        storage.fail=true;
        error(IMAGE_UPLOAD_FAILED,()->evidences.upload("rr_t",id,image));
        assertEquals(0,storage.assets.size());
        storage.fail=false;storage.onUpload=()->reports.assign("rr_a",id);
        error(REPORT_NOT_PENDING,()->evidences.upload("rr_t",id,image));
        assertEquals(0,storage.assets.size());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM report_evidences",Integer.class));
    }
    @Test void failedEvidenceInsertRollsBackDatabaseAndCleansUploadedAsset() throws Exception {
        String id=listingReport();var image=image();
        jdbc.execute("CREATE TRIGGER rr_fail_evidence BEFORE INSERT ON report_evidences FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Simulated evidence persistence failure'");
        try {
            assertThrows(org.springframework.dao.DataAccessException.class,()->evidences.upload("rr_t",id,image));
            assertEquals(0,storage.assets.size());
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM report_evidences",Integer.class));
        } finally {jdbc.execute("DROP TRIGGER rr_fail_evidence");}
    }
    @Test void databaseGuardsPreserveHistoryAndRejectInvalidTargetsAndRatings() {
        reviews.create("rr_t",ReviewType.ROOM,review("rr_active",4));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("DELETE FROM rental_agreements WHERE id='rr_active'"));
        listingReport();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("DELETE FROM listings WHERE id='rr_listing'"));
        var invalidTarget=assertThrows(org.springframework.dao.DataAccessException.class,
            ()->jdbc.update("INSERT INTO reports(id,tenant_id,reason_code) VALUES('bad','rr_t','SCAM')"));
        assertEquals(3819,((java.sql.SQLException)invalidTarget.getMostSpecificCause()).getErrorCode());
        var invalidRating=assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE room_reviews SET rating=6"));
        assertEquals(3819,((java.sql.SQLException)invalidRating.getMostSpecificCause()).getErrorCode());
    }
    @Test void realSecurityChainProtectsPrivateRoutesAndExposesOnlySafePublicDtos() throws Exception {
        reviews.create("rr_t",ReviewType.ROOM,review("rr_active",4));
        mvc.perform(get("/api/listings/rr_listing/reviews")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.data[0].authorName").exists())
            .andExpect(jsonPath("$.data.data[0].rentalAgreementId").doesNotExist())
            .andExpect(jsonPath("$.data.data[0].hiddenReason").doesNotExist())
            .andExpect(jsonPath("$.data.data[0].tenant").doesNotExist());
        mvc.perform(get("/api/listings/rr_listing/rooms/rr_r1/reviews/summary")).andExpect(status().isOk());
        mvc.perform(get("/api/listings/rr_listing/reviews").param("target","ROOM").param("roomId","rr_r1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary.totalReviews").value(1))
            .andExpect(jsonPath("$.data.reviews.data[0].verifiedStay").value(true))
            .andExpect(jsonPath("$.data.reviews.data[0].rentalAgreementId").doesNotExist());
        var ownerReview=reviews.create("rr_t",ReviewType.LANDLORD,review("rr_active",5));
        reviews.reply("rr_l",ReviewType.LANDLORD,ownerReview.id(),new ReviewReplyRequest("Thank you"));
        mvc.perform(get("/api/listings/rr_listing/reviews").param("target","LANDLORD"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary.replyRate").value(100))
            .andExpect(jsonPath("$.data.summary.totalReviews").value(1));
        mvc.perform(get("/api/listings/rr_listing/reviews").param("target","ROOM").param("roomId","rr_r3"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/landlords/rr_l/reviews")).andExpect(status().isOk());
        for(String route:List.of("/api/reviews/mine?type=ROOM","/api/tenants/rr_t/reviews","/api/admin/reviews?type=ROOM","/api/reports/mine"))
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/reports").with(user("rr_t").roles("TENANT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/tenants/rr_t/reviews").with(user("rr_n").roles("LANDLORD"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/listings/rr_listing/reviews?size=51")).andExpect(status().isBadRequest());
    }
    @Test void jsonRejectsFractionalRatingsUnknownAuthorityFieldsAndMutationRoutes() throws Exception {
        for(String rating:List.of("1.5","\"5\"","0","6")) {
            mvc.perform(post("/api/reviews/rooms").with(user("rr_t").roles("TENANT")).contentType(APPLICATION_JSON)
                .content("{\"rentalAgreementId\":\"rr_active\",\"rating\":"+rating+"}")).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/reviews/rooms").with(user("rr_t").roles("TENANT")).contentType(APPLICATION_JSON)
            .content("{\"rentalAgreementId\":\"rr_active\",\"rating\":5,\"tenantId\":\"rr_u\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/reports").with(user("rr_t").roles("TENANT")).contentType(APPLICATION_JSON)
            .content("{\"targetType\":\"LISTING\",\"targetId\":\"rr_listing\",\"reasonCode\":\"SCAM\",\"status\":\"RESOLVED\"}"))
            .andExpect(status().isBadRequest());
        var review=reviews.create("rr_t",ReviewType.ROOM,review("rr_active",5));
        mvc.perform(delete("/api/reviews/rooms/"+review.id()).with(user("rr_t").roles("TENANT"))).andExpect(status().isNotFound());
        mvc.perform(put("/api/reviews/rooms/"+review.id()).with(user("rr_t").roles("TENANT")).contentType(APPLICATION_JSON)
            .content("{\"rating\":1}")).andExpect(status().isNotFound());
        assertEquals(5,reviews.listing("rr_listing",null,0,10).getData().getFirst().rating());
    }
    @Test void privateImageEndpointReturnsNoStoreAndRejectsAnonymousAndOtherTenants() throws Exception {
        String id=listingReport();var proof=evidences.upload("rr_t",id,image());
        mvc.perform(get(proof.contentUrl())).andExpect(status().isUnauthorized());
        mvc.perform(get(proof.contentUrl()).with(user("rr_u").roles("TENANT"))).andExpect(status().isForbidden());
        mvc.perform(get(proof.contentUrl()).with(user("rr_t").roles("TENANT"))).andExpect(status().isOk())
            .andExpect(header().string("Content-Type","image/png"))
            .andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")));
    }
    @Test void legacyPublicEvidenceUrlsAreNeverReturnedOrFollowed() {
        String id=listingReport();
        jdbc.update("INSERT INTO report_evidences(id,report_id,image_url,image_public_id) VALUES('rr_legacy',?,'https://example.invalid/public.png','legacy-public-id')",id);
        assertEquals("/api/reports/"+id+"/evidences/rr_legacy/content",
            reports.detail("rr_t",id).evidences().getFirst().contentUrl());
        error(REPORT_EVIDENCE_UNAVAILABLE,()->evidences.download("rr_t",id,"rr_legacy"));
    }

    @Test void landlordReceivesOwnRoomAndLandlordReviewsAfterListingsExpireOrChangeOwner() {
        var room=reviews.create("rr_t",ReviewType.ROOM,review("rr_active",5));
        var landlord=reviews.create("rr_t",ReviewType.LANDLORD,review("rr_active",4));
        var otherRoom=reviews.create("rr_t",ReviewType.ROOM,review("rr_history",3));
        reviews.create("rr_t",ReviewType.LANDLORD,review("rr_history",2));
        jdbc.update("UPDATE listings SET status='EXPIRED',expires_at='2026-10-15'");
        jdbc.update("UPDATE properties SET landlord_id='rr_m' WHERE id='rr_p1'");

        var received=reviews.received("rr_l",ReviewType.ROOM,0,10);
        assertEquals(1,received.getTotalElements());
        var item=received.getData().getFirst();
        assertEquals(room.id(),item.review().id());
        assertEquals("rr_r1",item.targetId());assertEquals("Test P - 101",item.targetName());
        assertEquals("rr_active",item.rentalAgreementId());assertEquals("rr_active",item.agreementCode());
        assertEquals("rr_r1",item.roomId());assertEquals("101",item.roomNumber());
        assertEquals("rr_p1",item.propertyId());assertEquals("Test P",item.propertyName());assertTrue(item.canReply());
        assertEquals(otherRoom.id(),reviews.received("rr_m",ReviewType.ROOM,0,10).getData().getFirst().review().id());
        var receivedLandlord=reviews.received("rr_l",ReviewType.LANDLORD,0,10).getData().getFirst();
        assertEquals(landlord.id(),receivedLandlord.review().id());
        assertEquals("rr_l",receivedLandlord.targetId());assertEquals("Name rr_l",receivedLandlord.targetName());
        assertEquals("rr_active",receivedLandlord.rentalAgreementId());assertTrue(receivedLandlord.canReply());
        reviews.reply("rr_l",ReviewType.ROOM,room.id(),new ReviewReplyRequest("Reply"));
        assertTrue(reviews.received("rr_l",ReviewType.ROOM,0,10).getData().getFirst().canReply());

        reviews.visibility("rr_a",ReviewType.ROOM,room.id(),new ReviewVisibilityRequest(true,"Private reason"));
        reviews.visibility("rr_a",ReviewType.LANDLORD,landlord.id(),new ReviewVisibilityRequest(true,"Private reason"));
        assertEquals(0,reviews.received("rr_l",ReviewType.ROOM,0,10).getTotalElements());
        assertEquals(0,reviews.received("rr_l",ReviewType.LANDLORD,0,10).getTotalElements());
        assertEquals(0,reviews.received("rr_n",ReviewType.ROOM,0,10).getTotalElements());
    }
    @Test void receivedReviewApiRequiresLandlordAndDoesNotExposeModerationMetadata() throws Exception {
        var review=reviews.create("rr_t",ReviewType.ROOM,review("rr_active",5));
        mvc.perform(get("/api/landlord/reviews?type=ROOM")).andExpect(status().isUnauthorized());
        for(String role:List.of("TENANT","ADMIN"))
            mvc.perform(get("/api/landlord/reviews?type=ROOM").with(user("rr_t").roles(role))).andExpect(status().isForbidden());
        for(String query:List.of("?type=TENANT","?type=OTHER","?type=ROOM&size=51",""))
            mvc.perform(get("/api/landlord/reviews"+query).with(user("rr_l").roles("LANDLORD"))).andExpect(status().isBadRequest());
        error(UNAUTHORIZED,()->reviews.received("rr_t",ReviewType.ROOM,0,10));
        jdbc.update("UPDATE listings SET status='EXPIRED',expires_at='2026-10-15'");
        mvc.perform(get("/api/landlord/reviews?type=ROOM&landlordId=rr_m").with(user("rr_l").roles("LANDLORD")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.data[0].review.id").value(review.id()))
            .andExpect(jsonPath("$.data.data[0].targetId").value("rr_r1"))
            .andExpect(jsonPath("$.data.data[0].agreementCode").value("rr_active"))
            .andExpect(jsonPath("$.data.data[0].roomNumber").value("101"))
            .andExpect(jsonPath("$.data.data[0].canReply").value(true))
            .andExpect(jsonPath("$.data.data[0].hiddenReason").doesNotExist())
            .andExpect(jsonPath("$.data.data[0].hiddenBy").doesNotExist())
            .andExpect(jsonPath("$.data.data[0].tenantId").doesNotExist());
    }
    @Test void managementReviewsIncludeTargetIdentityForAllThreeTypes() throws Exception {
        reviews.create("rr_t",ReviewType.ROOM,review("rr_active",5));
        reviews.create("rr_t",ReviewType.LANDLORD,review("rr_active",4));
        var tenant=reviews.create("rr_l",ReviewType.TENANT,review("rr_active",3));
        var room=reviews.mine("rr_t",ReviewType.ROOM,0,10).getData().getFirst();
        assertEquals("rr_r1",room.targetId());assertEquals("Test P - 101",room.targetName());
        var landlord=reviews.mine("rr_t",ReviewType.LANDLORD,0,10).getData().getFirst();
        assertEquals("rr_l",landlord.targetId());assertEquals("Name rr_l",landlord.targetName());
        var reviewedTenant=reviews.admin("rr_a",ReviewType.TENANT,null,null,0,10).getData().getFirst();
        assertEquals("rr_t",reviewedTenant.targetId());assertEquals("Name rr_t",reviewedTenant.targetName());
        var hidden=reviews.visibility("rr_a",ReviewType.TENANT,tenant.id(),new ReviewVisibilityRequest(true,"Reason"));
        assertEquals("rr_t",hidden.targetId());assertEquals("Name rr_t",hidden.targetName());
        mvc.perform(get("/api/admin/reviews?type=TENANT").with(user("rr_a").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.data[0].targetId").value("rr_t"))
            .andExpect(jsonPath("$.data.data[0].targetName").value("Name rr_t"));
        mvc.perform(get("/api/listings/rr_listing/reviews")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.data[0].targetId").doesNotExist())
            .andExpect(jsonPath("$.data.data[0].rentalAgreementId").doesNotExist());
    }
    @Test void reportResponsesIncludeTargetAndHandlingAdminNames() throws Exception {
        var listing=reports.create("rr_t",report(ReportTargetType.LISTING,"rr_listing"));
        assertEquals("Test Listing",listing.targetName());assertNull(listing.handledByName());
        var landlord=reports.create("rr_t",report(ReportTargetType.LANDLORD,"rr_l"));
        assertEquals("Name rr_l",landlord.targetName());
        var assigned=reports.assign("rr_a",listing.id());
        assertEquals("rr_a",assigned.handledBy());assertEquals("Name rr_a",assigned.handledByName());
        var closed=reports.close("rr_a",listing.id(),ReportStatus.RESOLVED,new ReportResolutionRequest("Done"));
        assertEquals("Test Listing",closed.targetName());assertEquals("Name rr_a",closed.handledByName());
        assertEquals("Name rr_a",reports.detail("rr_t",listing.id()).report().handledByName());
        assertTrue(reports.mine("rr_t",null,0,10).getData().stream().allMatch(r->r.targetName()!=null));
        mvc.perform(get("/api/admin/reports?status=RESOLVED").with(user("rr_a").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.data[0].targetName").value("Test Listing"))
            .andExpect(jsonPath("$.data.data[0].handledByName").value("Name rr_a"));
        mvc.perform(get("/api/reports/"+listing.id()).with(user("rr_t").roles("TENANT")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.report.handledByName").value("Name rr_a"));
    }

    static class FakeStorage implements ReportEvidenceStorage {
        final Map<String,byte[]> assets=new ConcurrentHashMap<>();
        volatile boolean fail;
        volatile Runnable onUpload=()->{};
        public void upload(String id,byte[] bytes) throws Exception {
            assets.put(id,bytes);if(fail)throw new java.io.IOException("Upload failure");onUpload.run();
        }
        public byte[] download(String id) {return Objects.requireNonNull(assets.get(id));}
        public void delete(String id){assets.remove(id);}
    }
    @Configuration @EnableWebMvc @EnableMethodSecurity @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses={
        RoomReviewRepository.class,ReportRepository.class,NotificationRepository.class,
        UserRepository.class,com.tamdaptit.rentalmanagement.rental.repository.RentalAgreementRepository.class,
        com.tamdaptit.rentalmanagement.room.repository.RoomRepository.class,
        com.tamdaptit.rentalmanagement.listing.repository.ListingRepository.class})
    @Import({com.tamdaptit.rentalmanagement.notification.service.NotificationPublisher.class,ReviewServiceImpl.class,ReportServiceImpl.class,com.tamdaptit.rentalmanagement.report.service.impl.ReportEvidenceServiceImpl.class,ReviewController.class,
        com.tamdaptit.rentalmanagement.review.service.impl.ListingReviewServiceImpl.class,
        com.tamdaptit.rentalmanagement.review.controller.ListingReviewController.class,
        ReportController.class,GlobalExceptionHandler.class,SecurityConfig.class,JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,ObjectMapperConfig.class,ImageValidator.class})
    static class Config {
        @Bean Clock clock(){return Clock.fixed(Instant.parse("2026-11-03T03:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh"));}
        @Bean FakeStorage storage(){return new FakeStorage();}
        @Bean JwtTokenService jwt(){return org.mockito.Mockito.mock(JwtTokenService.class);}
        @Bean ReviewMapper reviewsMapper(){return org.mapstruct.factory.Mappers.getMapper(ReviewMapper.class);}
        @Bean ReportMapper reportsMapper(){return org.mapstruct.factory.Mappers.getMapper(ReportMapper.class);}
        @Bean DataSource dataSource(){return new DriverManagerDataSource(url(schema),dbUser(),password());}
        @Bean JdbcTemplate jdbc(DataSource ds){return new JdbcTemplate(ds);}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){
            var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(ds);
            factory.setPackagesToScan("com.tamdaptit.rentalmanagement");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate"));return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory){return new JpaTransactionManager(factory);}
    }
}
