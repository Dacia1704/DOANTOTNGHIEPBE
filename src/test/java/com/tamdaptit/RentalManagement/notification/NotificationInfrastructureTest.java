package com.tamdaptit.rentalmanagement.notification;

import com.tamdaptit.rentalmanagement.auth.service.JwtTokenService;
import com.tamdaptit.rentalmanagement.common.config.*;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.*;
import com.tamdaptit.rentalmanagement.common.security.JwtAuthenticationFilter;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.notification.config.NotificationRabbitConfig;
import com.tamdaptit.rentalmanagement.notification.controller.NotificationController;
import com.tamdaptit.rentalmanagement.notification.dto.NotificationEvent;
import com.tamdaptit.rentalmanagement.notification.entity.enums.NotificationType;
import com.tamdaptit.rentalmanagement.notification.service.*;
import com.tamdaptit.rentalmanagement.property.dto.response.PropertyTypeResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.*;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import javax.sql.DataSource;
import java.net.URI;
import java.net.http.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import static com.tamdaptit.rentalmanagement.notification.config.NotificationRabbitConfig.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MySQL/Redis/RabbitMQ, in a disposable schema, vhost and uniquely named Redis cache. */
@EnabledIfEnvironmentVariable(named="NOTIFICATION_INFRA_TEST",matches="true")
class NotificationInfrastructureTest {
    static final String suffix=UUID.randomUUID().toString().replace("-","");
    static final String schema="notification_test_"+suffix, vhost="notification-test-"+suffix;
    static AnnotationConfigWebApplicationContext context;
    static JdbcTemplate jdbc;
    static RabbitTemplate rabbit;
    static NotificationPublisher publisher;
    static NotificationOutboxRelay relay;
    static NotificationConsumer consumer;
    static MockMvc mvc;
    static final ObjectMapper mapper=new ObjectMapper().registerModule(new JavaTimeModule());
    static String env(String key,String fallback){return System.getenv().getOrDefault(key,fallback);}
    static String url(String db){return env("SUBSCRIPTION_TEST_MYSQL_URL","jdbc:mysql://127.0.0.1:3306/")+db+"?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh";}
    static String dbUser(){return env("SUBSCRIPTION_TEST_MYSQL_USER","root");}
    static String password(){return env("SUBSCRIPTION_TEST_MYSQL_PASSWORD","");}
    static void management(String method,String path,String body)throws Exception {
        String credentials=env("RABBITMQ_USER","admin")+":"+env("RABBITMQ_PASSWORD","admin123");
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+env("RABBITMQ_MANAGEMENT_PORT","15672")+"/api/"+path))
            .header("Authorization","Basic "+Base64.getEncoder().encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body)).build();
        var response=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
            .send(request,HttpResponse.BodyHandlers.discarding());
        assertTrue(response.statusCode()<300,"RabbitMQ management HTTP "+response.statusCode());
    }
    @BeforeAll static void start()throws Exception {
        try(var c=DriverManager.getConnection(url(""),dbUser(),password());var s=c.createStatement()){
            s.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");
        }
        Flyway.configure().dataSource(url(schema),dbUser(),password()).load().migrate();
        management("PUT","vhosts/"+vhost,"{}");
        management("PUT","permissions/"+vhost+"/"+env("RABBITMQ_USER","admin"),"{\"configure\":\".*\",\"write\":\".*\",\"read\":\".*\"}");
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        context.register(Config.class);context.refresh();
        jdbc=context.getBean(JdbcTemplate.class);rabbit=context.getBean(RabbitTemplate.class);
        context.getBean(RabbitAdmin.class).initialize();
        publisher=context.getBean(NotificationPublisher.class);relay=context.getBean(NotificationOutboxRelay.class);
        consumer=context.getBean(NotificationConsumer.class);
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for(String id:List.of("notification-a","notification-b"))
            jdbc.update("INSERT INTO users(id,email,password,full_name) VALUES(?,?,?,?)",id,id+"@test.local","test",id);
    }
    @AfterAll static void stop()throws Exception {
        if(context!=null)context.close();
        try { management("DELETE","vhosts/"+vhost,""); }
        finally {
            try(var c=DriverManager.getConnection(url(""),dbUser(),password());var s=c.createStatement()){
                s.execute("DROP DATABASE IF EXISTS `"+schema+"`");
            }
        }
    }
    @BeforeEach void reset(){
        jdbc.update("DELETE FROM notification_outbox");jdbc.update("DELETE FROM notifications WHERE user_id LIKE 'notification-%'");
        context.getBean(RabbitAdmin.class).purgeQueue(QUEUE);context.getBean(RabbitAdmin.class).purgeQueue(DEAD_QUEUE);
        clearInvocations(context.getBean(SimpMessagingTemplate.class));
    }
    void enqueue(String user){publisher.enqueue(user,NotificationType.SYSTEM,"Thông báo thử","Nội dung","LISTING","listing-test");}
    Message take(){var message=rabbit.receive(QUEUE,3000);assertNotNull(message);return message;}

    @Test void intentRollsBackWithBusinessTransaction(){
        var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThrows(IllegalStateException.class,()->tx.execute(status->{enqueue("notification-a");throw new IllegalStateException("rollback");}));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox",Integer.class));
    }
    @Test void confirmedDeliveryIsDurableAndRedeliveryPreservesReadState()throws Exception {
        enqueue("notification-a");relay.publishPending();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE published_at IS NOT NULL",Integer.class));
        var message=take();assertEquals(MessageDeliveryMode.PERSISTENT,message.getMessageProperties().getReceivedDeliveryMode());
        consumer.receive(message);consumer.receive(message);
        assertEquals(1,context.getBean(NotificationService.class).unreadCount("notification-a"));
        verify(context.getBean(SimpMessagingTemplate.class),times(1)).convertAndSendToUser(eq("notification-a"),eq("/queue/notifications"),any(Object.class));
        String id=mapper.readValue(message.getBody(),NotificationEvent.class).id();
        context.getBean(NotificationService.class).markRead("notification-a",id);consumer.receive(message);
        assertEquals(0,context.getBean(NotificationService.class).unreadCount("notification-a"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE id=?",Integer.class,id));
    }
    @Test void unroutablePublishStaysPendingAndCanBeRetried(){
        context.getBean(RabbitAdmin.class).deleteQueue(QUEUE);
        try {
            enqueue("notification-a");relay.publishPending();
            assertEquals(1,jdbc.queryForObject("SELECT attempts FROM notification_outbox",Integer.class));
            assertNull(jdbc.queryForObject("SELECT published_at FROM notification_outbox",java.sql.Timestamp.class));
        } finally { context.getBean(RabbitAdmin.class).initialize(); }
        jdbc.update("UPDATE notification_outbox SET next_attempt_at=created_at");relay.publishPending();take();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE published_at IS NOT NULL",Integer.class));
    }
    @Test void rejectedMessagesReachDurableDeadLetterQueue(){
        rabbit.send(EXCHANGE,ROUTING_KEY,MessageBuilder.withBody("bad-json".getBytes()).build());
        rabbit.execute(channel->{
            var response=channel.basicGet(QUEUE,false);assertNotNull(response);
            channel.basicReject(response.getEnvelope().getDeliveryTag(),false);return null;
        });
        assertNotNull(rabbit.receive(DEAD_QUEUE,3000));
    }
    @Test void apiScopesPaginationReadActionsAndRejectsAnonymousRequests()throws Exception {
        enqueue("notification-a");enqueue("notification-a");enqueue("notification-b");relay.publishPending();
        for(int i=0;i<3;i++)consumer.receive(take());
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications").with(user("notification-a")).param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2)).andExpect(jsonPath("$.data.data.length()").value(1));
        String other=jdbc.queryForObject("SELECT id FROM notifications WHERE user_id='notification-b'",String.class);
        mvc.perform(put("/api/notifications/"+other+"/read").with(user("notification-a"))).andExpect(status().isNotFound());
        mvc.perform(put("/api/notifications/read-all").with(user("notification-a"))).andExpect(status().isOk());
        mvc.perform(get("/api/notifications/unread-count").with(user("notification-a"))).andExpect(jsonPath("$.data").value(0));
        mvc.perform(get("/api/notifications/unread-count").with(user("notification-b"))).andExpect(jsonPath("$.data").value(1));
        mvc.perform(get("/api/notifications").param("page","-1").with(user("notification-a"))).andExpect(status().isBadRequest());
    }
    @Test void consumerRollbackDoesNotPushUncommittedNotification()throws Exception {
        enqueue("notification-a");relay.publishPending();var message=take();
        var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        tx.execute(status->{try{consumer.receive(message);}catch(Exception ex){throw new RuntimeException(ex);}status.setRollbackOnly();return null;});
        assertEquals(0,context.getBean(NotificationService.class).unreadCount("notification-a"));
        verifyNoInteractions(context.getBean(SimpMessagingTemplate.class));
        consumer.receive(message);
        assertEquals(1,context.getBean(NotificationService.class).unreadCount("notification-a"));
    }
    @Test void redisRoundTripsImmutableDtoPagesWithTtlAndDefersEvictionUntilCommit(){
        var standalone=new RedisStandaloneConfiguration("127.0.0.1",Integer.parseInt(env("REDIS_PORT","6379")));
        standalone.setPassword(RedisPassword.of(env("REDIS_PASSWORD","redis123")));
        var connection=new LettuceConnectionFactory(standalone);connection.afterPropertiesSet();connection.start();
        String name="notification-test-"+suffix;
        try {
            var cache=new CacheConfig().cacheManager(connection,Duration.ofMinutes(2)).getCache(name);
            var page=PageResponse.<PropertyTypeResponse>builder().currentPage(0).pageSize(20).totalPages(1).totalElements(1)
                .data(List.of(new PropertyTypeResponse("id","Nhà trọ",null,LocalDateTime.now().withNano(0),null))).build();
            cache.put("page",page);
            var restored=cache.get("page",PageResponse.class);
            assertInstanceOf(PropertyTypeResponse.class,restored.getData().getFirst());assertEquals(page,restored);
            var redis=new StringRedisTemplate(connection);
            Long ttl=redis.getExpire("rental:v1:"+name+"::page");assertTrue(ttl>0 && ttl<=120);
            var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            tx.execute(status->{cache.clear();assertNotNull(cache.get("page"));status.setRollbackOnly();return null;});
            assertNotNull(cache.get("page"));
            tx.execute(status->{cache.clear();assertNotNull(cache.get("page"));return null;});
            assertNull(cache.get("page"));
        } finally {new StringRedisTemplate(connection).delete("rental:v1:"+name+"::page");connection.destroy();}
    }

    @Test void catalogServiceReadsHitRedisAndMutationInvalidatesAllPages(){
        var standalone=new RedisStandaloneConfiguration("127.0.0.1",Integer.parseInt(env("REDIS_PORT","6379")));
        standalone.setPassword(RedisPassword.of(env("REDIS_PASSWORD","redis123")));
        var connection=new LettuceConnectionFactory(standalone);connection.afterPropertiesSet();connection.start();
        var manager=new CacheConfig().cacheManager(connection,Duration.ofMinutes(2));
        var cache=manager.getCache("notification-catalog-test-"+suffix);
        try {
            var types=mock(com.tamdaptit.rentalmanagement.property.repository.PropertyTypeRepository.class);
            var properties=mock(com.tamdaptit.rentalmanagement.property.repository.PropertyRepository.class);
            var entity=com.tamdaptit.rentalmanagement.property.entity.PropertyType.builder().id("type-test").name("Original").build();
            when(types.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(entity)));
            when(types.findById("type-test")).thenReturn(Optional.of(entity));
            when(types.save(any())).thenAnswer(invocation->invocation.getArgument(0));
            var target=new com.tamdaptit.rentalmanagement.property.service.impl.PropertyTypeServiceImpl(types,properties,
                org.mapstruct.factory.Mappers.getMapper(com.tamdaptit.rentalmanagement.property.mapper.PropertyTypeMapper.class));
            var interceptor=new org.springframework.cache.interceptor.CacheInterceptor();
            interceptor.setCacheOperationSources(new org.springframework.cache.annotation.AnnotationCacheOperationSource());
            interceptor.setCacheManager(new org.springframework.cache.CacheManager(){
                public org.springframework.cache.Cache getCache(String name){return cache;}
                public Collection<String> getCacheNames(){return List.of(cache.getName());}
            });
            interceptor.afterPropertiesSet();interceptor.afterSingletonsInstantiated();
            var proxy=new org.springframework.aop.framework.ProxyFactory(target);proxy.addAdvice(interceptor);
            var service=(com.tamdaptit.rentalmanagement.property.service.PropertyTypeService)proxy.getProxy();
            assertEquals("Original",service.getAll(0,20).getData().getFirst().name());
            assertEquals("Original",service.getAll(0,20).getData().getFirst().name());
            verify(types,times(1)).findAll(any(org.springframework.data.domain.Pageable.class));
            service.update("type-test",new com.tamdaptit.rentalmanagement.property.dto.request.PropertyTypeRequest("Updated",null));
            assertEquals("Updated",service.getAll(0,20).getData().getFirst().name());
            verify(types,times(2)).findAll(any(org.springframework.data.domain.Pageable.class));
        } finally {cache.clear();connection.destroy();}
    }
    @Configuration @EnableTransactionManagement @EnableWebMvc
    @Import({NotificationPublisher.class,NotificationOutboxRelay.class,NotificationConsumer.class,NotificationRealtime.class,
        com.tamdaptit.rentalmanagement.notification.service.impl.NotificationServiceImpl.class,NotificationController.class,NotificationRabbitConfig.class,
        SecurityConfig.class,JwtAuthenticationEntryPoint.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class,ObjectMapperConfig.class})
    static class Config {
        @Bean Clock clock(){return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));}
        @Bean DataSource dataSource(){return new DriverManagerDataSource(url(schema),dbUser(),password());}
        @Bean JdbcTemplate jdbc(DataSource ds){return new JdbcTemplate(ds);}
        @Bean PlatformTransactionManager transactionManager(DataSource ds){return new DataSourceTransactionManager(ds);}
        @Bean SimpMessagingTemplate messaging(){return mock(SimpMessagingTemplate.class);}
        @Bean JwtTokenService jwt(){return mock(JwtTokenService.class);}
        @Bean UserRepository users(){return mock(UserRepository.class);}
        @Bean CachingConnectionFactory rabbitConnectionFactory(){
            var factory=new CachingConnectionFactory("127.0.0.1",Integer.parseInt(env("RABBITMQ_PORT","5672")));
            factory.setUsername(env("RABBITMQ_USER","admin"));factory.setPassword(env("RABBITMQ_PASSWORD","admin123"));
            factory.setVirtualHost(vhost);factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
            factory.setPublisherReturns(true);return factory;
        }
        @Bean RabbitAdmin admin(CachingConnectionFactory factory){return new RabbitAdmin(factory);}
        @Bean RabbitTemplate rabbit(CachingConnectionFactory factory){var template=new RabbitTemplate(factory);template.setMandatory(true);return template;}
    }
}
