package com.tamdaptit.rentalmanagement.chat;

import com.jayway.jsonpath.JsonPath;
import com.tamdaptit.rentalmanagement.auth.service.JwtTokenService;
import com.tamdaptit.rentalmanagement.auth.service.impl.JwtTokenServiceImpl;
import com.tamdaptit.rentalmanagement.chat.controller.ChatController;
import com.tamdaptit.rentalmanagement.chat.dto.request.ChatMessageRequest;
import com.tamdaptit.rentalmanagement.chat.dto.response.ChatMessageResponse;
import com.tamdaptit.rentalmanagement.chat.repository.ChatConversationRepository;
import com.tamdaptit.rentalmanagement.chat.service.ChatService;
import com.tamdaptit.rentalmanagement.chat.service.impl.ChatServiceImpl;
import com.tamdaptit.rentalmanagement.common.config.*;
import com.tamdaptit.rentalmanagement.common.exception.GlobalExceptionHandler;
import com.tamdaptit.rentalmanagement.common.security.JwtAuthenticationFilter;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import javax.sql.DataSource;
import java.sql.DriverManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Real MySQL/JPA + production REST/JWT security, isolated from the application database.
 * Loads the baseline and role metadata needed by JWT; this is not a full Flyway migration test. */
@EnabledIfEnvironmentVariable(named="CHAT_TEST_MYSQL_URL", matches="jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/")
class ChatSupportWorkflowTest {
    static String schema;
    static AnnotationConfigWebApplicationContext context;
    static JdbcTemplate jdbc;
    static MockMvc mvc;
    static ChatService chat;
    static SimpMessagingTemplate broker;
    static String dbUser() { return System.getenv().getOrDefault("CHAT_TEST_MYSQL_USER", "root"); }
    static String password() { return System.getenv().getOrDefault("CHAT_TEST_MYSQL_PASSWORD", ""); }
    static String url(String database) { return System.getenv("CHAT_TEST_MYSQL_URL")+database+"?serverTimezone=Asia/Ho_Chi_Minh&useSSL=false&allowPublicKeyRetrieval=true"; }

    @BeforeAll static void start() throws Exception {
        schema = "chat_support_test_"+UUID.randomUUID().toString().replace("-", "");
        try (var db=DriverManager.getConnection(url(""),dbUser(),password()); var sql=db.createStatement()) {
            sql.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");
        }
        var initializer = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V1__fullsql.sql"),
                new ClassPathResource("db/migration/V7__role_permission_management.sql"));
        initializer.execute(new DriverManagerDataSource(url(schema),dbUser(),password()));
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext()); context.register(Config.class); context.refresh();
        jdbc=context.getBean(JdbcTemplate.class); chat=context.getBean(ChatService.class);
        broker=context.getBean(SimpMessagingTemplate.class);
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for (String role:List.of("ADMIN","TENANT","LANDLORD"))
            jdbc.update("INSERT INTO roles(id,code,name,permission_version,created_at,updated_at) VALUES(?,?,?,0,NOW(),NOW())", "role-"+role,role,role);
        for (String id:List.of("support-admin","other-admin","client-tenant","client-landlord")) {
            String role=id.endsWith("admin")?"ADMIN":id.endsWith("tenant")?"TENANT":"LANDLORD";
            String email=id.equals("support-admin")?"admin@gmail.com":id+"@chat-test.invalid";
            jdbc.update("INSERT INTO users(id,email,password,full_name,status,auth_version,created_at,updated_at) VALUES(?,?,?,?,'ACTIVE',0,NOW(),NOW())",id,email,"test-only",id);
            jdbc.update("INSERT INTO user_roles(id,user_id,role_id) VALUES(?,?,?)",UUID.randomUUID().toString(),id,"role-"+role);
        }
    }
    @AfterAll static void cleanup() throws Exception {
        if (context!=null) context.close();
        if (schema!=null && schema.matches("chat_support_test_[a-f0-9]{32}")) {
            try (var db=DriverManager.getConnection(url(""),dbUser(),password()); var sql=db.createStatement()) {
                sql.execute("DROP DATABASE IF EXISTS `"+schema+"`");
            }
        }
    }
    @BeforeEach void reset() {
        jdbc.update("DELETE FROM chat_messages"); jdbc.update("DELETE FROM chat_conversation_participants"); jdbc.update("DELETE FROM chat_conversations");
        jdbc.update("UPDATE users SET status='ACTIVE'"); org.mockito.Mockito.reset(broker);
    }
    String token(String id) {
        return "Bearer "+context.getBean(JwtTokenService.class).generateAccessToken(context.getBean(UserRepository.class).findByIdWithRolesAndPermissions(id).orElseThrow());
    }
    String open(String client) throws Exception {
        var result=mvc.perform(post("/api/chat/conversations/support").header("Authorization",token(client)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.otherUser.id").value("support-admin"))
                .andExpect(jsonPath("$.data.listing").doesNotExist()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(),"$.data.id");
    }
    void send(String conversation,String sender,String content) {
        var request=new ChatMessageRequest(); request.setConversationId(conversation); request.setContent(content);
        chat.sendMessage(request,()->sender);
    }
    @Test void tenantOpensSupportAndBothPartiesCanExchangePersistedMessages() throws Exception {
        String id=open("client-tenant"); assertEquals(id,open("client-tenant"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM chat_conversations",Integer.class));
        mvc.perform(get("/api/chat/conversations").header("Authorization",token("support-admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].otherUser.id").value("client-tenant"));
        send(id,"client-tenant","Xin chào, tôi cần hỗ trợ.");
        verify(broker).convertAndSendToUser(eq("support-admin"),eq("/queue/messages"),any(ChatMessageResponse.class));
        verify(broker).convertAndSendToUser(eq("client-tenant"),eq("/queue/messages"),any(ChatMessageResponse.class));
        mvc.perform(get("/api/chat/conversations/"+id+"/messages").header("Authorization",token("support-admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.data[0].content").value("Xin chào, tôi cần hỗ trợ."));
        send(id,"support-admin","Chào bạn, quản trị viên đã nhận được tin.");
        mvc.perform(get("/api/chat/conversations/"+id+"/messages").header("Authorization",token("client-tenant")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(put("/api/chat/conversations/"+id+"/read").header("Authorization",token("support-admin"))).andExpect(status().isOk());
        mvc.perform(get("/api/chat/conversations").header("Authorization",token("support-admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].unreadCount").value(0));
    }
    @Test void landlordUsesTheSameSupportAdminAndUnrelatedAccountsHaveNoAccess() throws Exception {
        String id=open("client-landlord");
        mvc.perform(get("/api/chat/conversations").header("Authorization",token("other-admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/chat/conversations/"+id+"/messages").header("Authorization",token("other-admin"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/chat/conversations/"+id+"/messages").header("Authorization",token("client-tenant"))).andExpect(status().isForbidden());
    }
    @Test void inactiveSupportAccountReturnsUnavailableWithoutRoutingToAnotherAdmin() throws Exception {
        jdbc.update("UPDATE users SET status='INACTIVE' WHERE id='support-admin'");
        mvc.perform(post("/api/chat/conversations/support").header("Authorization",token("client-tenant")))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.errorCode").value("SUPPORT_ADMIN_UNAVAILABLE"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM chat_conversations",Integer.class));
    }
    @Test void anonymousAndAdminCannotStartClientSupportChats() throws Exception {
        mvc.perform(post("/api/chat/conversations/support")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/chat/conversations/support").header("Authorization",token("support-admin"))).andExpect(status().isForbidden());
    }

    @Configuration @EnableWebMvc @EnableMethodSecurity @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses={UserRepository.class,ChatConversationRepository.class,ListingRepository.class})
    @Import({ChatController.class,ChatServiceImpl.class,GlobalExceptionHandler.class,SecurityConfig.class,
            JwtAuthenticationFilter.class,JwtAuthenticationEntryPoint.class,ObjectMapperConfig.class})
    static class Config {
        @Bean SimpMessagingTemplate broker() { return mock(SimpMessagingTemplate.class); }
        @Bean JwtTokenService jwt() { return new JwtTokenServiceImpl("test-only-chat-key-".repeat(5),"chat-test",60000,120000); }
        @Bean DataSource dataSource() { return new DriverManagerDataSource(url(schema),dbUser(),password()); }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory=new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(ds);
            factory.setPackagesToScan("com.tamdaptit.rentalmanagement"); factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none")); return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
    }
}
