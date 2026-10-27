package com.tamdaptit.rentalmanagement.property;

import com.jayway.jsonpath.JsonPath;
import com.tamdaptit.rentalmanagement.property.controller.*;
import com.tamdaptit.rentalmanagement.property.service.impl.*;
import com.tamdaptit.rentalmanagement.property.mapper.*;
import com.tamdaptit.rentalmanagement.property.repository.PropertyRepository;
import com.tamdaptit.rentalmanagement.room.controller.RoomController;
import com.tamdaptit.rentalmanagement.room.service.impl.RoomServiceImpl;
import com.tamdaptit.rentalmanagement.room.mapper.RoomMapper;
import com.tamdaptit.rentalmanagement.room.repository.RoomRepository;
import com.tamdaptit.rentalmanagement.address.repository.ProvinceRepository;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.common.exception.GlobalExceptionHandler;
import com.tamdaptit.rentalmanagement.storage.service.ImageStorageService;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mapstruct.factory.Mappers;
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
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import javax.sql.DataSource;
import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@EnabledIfEnvironmentVariable(named = "SUBSCRIPTION_TEST_MYSQL_URL",
        matches = "jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/")
class PropertyApiTest {
    private static String schema;
    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mvc;
    private static JdbcTemplate jdbc;
    private static String dbUser() { return System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_USER", "root"); }
    private static String password() { return System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_PASSWORD", ""); }
    private static String url(String db) { return System.getenv("SUBSCRIPTION_TEST_MYSQL_URL") + db
            + "?serverTimezone=Asia/Ho_Chi_Minh&useSSL=false&allowPublicKeyRetrieval=true"; }

    @BeforeAll
    static void start() throws Exception {
        schema = "property_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(url(""), dbUser(), password()); var s = c.createStatement()) {
            s.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
        Flyway.configure().dataSource(url(schema), dbUser(), password()).load().migrate();
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc = context.getBean(JdbcTemplate.class);
        jdbc.update("INSERT INTO users(id,email,password) VALUES ('owner','owner@property.test','test'),('other','other@property.test','test')");
        jdbc.update("INSERT INTO provinces(id,code,name) VALUES ('p','PROP-P','Province')");
        jdbc.update("INSERT INTO wards(id,province_id,code,name) VALUES ('w','p','PROP-W','Ward')");
        jdbc.update("INSERT INTO room_types(id,name) VALUES ('rt','Property test room')");
        jdbc.update("INSERT INTO amenities(id,name) VALUES ('a','Property test amenity')");
    }

    @BeforeEach
    void reset() {
        for (String table : new String[]{"room_amenities", "room_images", "property_amenities", "property_images", "rooms", "properties"})
            jdbc.update("DELETE FROM " + table);
        jdbc.update("DELETE FROM property_types WHERE id <> '00000000-0000-0000-0000-000000000011'");
    }

    @AfterAll
    static void close() throws Exception {
        if (context != null) context.close();
        if (schema != null && schema.matches("property_test_[a-f0-9]{32}"))
            try (var c = DriverManager.getConnection(url(""), dbUser(), password()); var s = c.createStatement()) {
                s.execute("DROP DATABASE `" + schema + "`");
            }
    }

    private String body(String mode, String rooms) {
        return """
                {"name":"Cơ sở A","propertyTypeId":"00000000-0000-0000-0000-000000000011",
                 "rentalMode":"%s","provinceId":"p","wardId":"w","addressLine":"123 Test",
                 "amenityIds":["a","a"],
                 "defaultRoom":{"roomTypeId":"rt","price":3000000,"electricityPrice":3500,
                 "waterPrice":100000,"serviceFee":50000,"defaultDeposit":3000000,"areaM2":25,"maxOccupants":2},
                 "floors":[{"floorNumber":1,"rooms":%s}]}
                """.formatted(mode, rooms);
    }

    private String create(String body) throws Exception {
        return mvc.perform(post("/api/properties").with(user("owner").roles("LANDLORD"))
                .contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void createsRoomsWithDefaultsOverridesAndSeparateAmenities() throws Exception {
        String response = create(body("BY_ROOM", """
                [{"roomNumber":"A101","amenityIds":["a","a"]},
                 {"roomNumber":"A102","price":3500000,"serviceFee":0,"areaM2":30}]
                """));
        assertEquals("owner", JsonPath.read(response, "$.data.landlordId"));
        assertEquals(2, (int) JsonPath.read(response, "$.data.rooms.length()"));
        assertEquals(3000000d, ((Number) JsonPath.read(response, "$.data.rooms[0].price")).doubleValue());
        assertEquals(3500000d, ((Number) JsonPath.read(response, "$.data.rooms[1].price")).doubleValue());
        assertEquals(0d, ((Number) JsonPath.read(response, "$.data.rooms[1].serviceFee")).doubleValue());
        assertEquals("AVAILABLE", JsonPath.read(response, "$.data.rooms[0].rentalStatus"));
        assertEquals("123 Test", JsonPath.read(response, "$.data.rooms[0].addressLine"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM property_amenities", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM room_amenities", Integer.class));
        mvc.perform(get("/api/rooms/mine").with(user("owner").roles("LANDLORD")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
        String id = JsonPath.read(response, "$.data.id");
        mvc.perform(get("/api/properties/" + id).with(user("other").roles("LANDLORD"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/properties/mine").with(user("other").roles("LANDLORD")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        // Identical room identifiers are allowed in different properties.
        create(body("BY_ROOM", "[{\"roomNumber\":\"A101\"}]"));
    }

    @Test
    void rejectsDuplicateNumbersInvalidCountsAndMergedValues() throws Exception {
        for (String request : new String[]{body("BY_ROOM", "[]"), body("WHOLE", "[]"),
                body("WHOLE", "[{\"roomNumber\":\"A\"},{\"roomNumber\":\"B\"}]"),
                body("BY_ROOM", "[{\"roomNumber\":\"A\",\"price\":0}]"),
                body("BY_ROOM", "[{\"roomNumber\":\"A\"}]").replace("\"maxOccupants\":2", "\"maxOccupants\":null")}) {
            mvc.perform(post("/api/properties").with(user("owner").roles("LANDLORD"))
                    .contentType(APPLICATION_JSON).content(request)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/properties").with(user("owner").roles("LANDLORD"))
                .contentType(APPLICATION_JSON).content(body("BY_ROOM", "[{\"roomNumber\":\"A\"},{\"roomNumber\":\" a \"}]")))
                .andExpect(status().isConflict());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM properties", Integer.class));
    }

    @Test
    void failedSecondRoomRollsBackPropertyFirstRoomAndAmenities() throws Exception {
        mvc.perform(post("/api/properties").with(user("owner").roles("LANDLORD"))
                .contentType(APPLICATION_JSON).content(body("BY_ROOM",
                        "[{\"roomNumber\":\"A\",\"amenityIds\":[\"a\"]},{\"roomNumber\":\"B\",\"roomTypeId\":\"missing\"}]")))
                .andExpect(status().isNotFound());
        for (String table : new String[]{"properties", "rooms", "property_amenities", "room_amenities"})
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
    }

    @Test
    void wholeHasOneUnitAndCannotAddOrRemoveItsOnlyUnit() throws Exception {
        String response = create(body("WHOLE", "[{\"roomNumber\":\"HOUSE\"}]"));
        String propertyId = JsonPath.read(response, "$.data.id");
        String roomId = JsonPath.read(response, "$.data.rooms[0].id");
        String room = """
                {"propertyId":"%s","roomNumber":"EXTRA","roomTypeId":"rt",
                 "price":100,"areaM2":20,"maxOccupants":2}
                """.formatted(propertyId);
        mvc.perform(multipart("/api/rooms").file(new org.springframework.mock.web.MockMultipartFile(
                "room", "room.json", "application/json", room.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .with(user("owner").roles("LANDLORD"))).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/rooms/" + roomId).with(user("owner").roles("LANDLORD"))).andExpect(status().isBadRequest());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM rooms", Integer.class));
    }

    @Test
    void roomUpdatesPreserveSharedAddressAndEnforceOwnerAndUniqueNumber() throws Exception {
        String response = create(body("BY_ROOM", "[{\"roomNumber\":\"A\",\"amenityIds\":[\"a\"]},{\"roomNumber\":\"B\"}]"));
        String propertyId = JsonPath.read(response, "$.data.id");
        String roomId = JsonPath.read(response, "$.data.rooms[0].id");
        String request = """
                {"propertyId":"%s","roomNumber":"A","roomTypeId":"rt",
                 "price":200,"areaM2":30,"maxOccupants":3,"amenityIds":["a"]}
                """.formatted(propertyId);
        mvc.perform(put("/api/rooms/" + roomId).with(user("owner").roles("LANDLORD"))
                .contentType(APPLICATION_JSON).content(request)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressLine").value("123 Test"))
                .andExpect(jsonPath("$.data.amenities.length()").value(1));
        mvc.perform(put("/api/rooms/" + roomId).with(user("owner").roles("LANDLORD"))
                .contentType(APPLICATION_JSON).content(request.replace("\"roomNumber\":\"A\"", "\"roomNumber\":\"B\"")))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/rooms/" + roomId).with(user("other").roles("LANDLORD"))
                .contentType(APPLICATION_JSON).content(request)).andExpect(status().isForbidden());
    }

    @Test
    void adminCrudAndRoleChecks() throws Exception {
        mvc.perform(post("/api/property-types").with(user("owner").roles("LANDLORD"))
                .contentType(APPLICATION_JSON).content("{\"name\":\"Nhà trọ\"}")).andExpect(status().isForbidden());
        String response = mvc.perform(post("/api/property-types").with(user("admin").roles("ADMIN"))
                .contentType(APPLICATION_JSON).content("{\"name\":\"Nhà trọ\"}")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.data.id");
        mvc.perform(put("/api/property-types/" + id).with(user("admin").roles("ADMIN"))
                .contentType(APPLICATION_JSON).content("{\"name\":\"Căn hộ\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/property-types/" + id).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        create(body("BY_ROOM", "[{\"roomNumber\":\"A\"}]"));
        mvc.perform(delete("/api/property-types/00000000-0000-0000-0000-000000000011").with(user("admin").roles("ADMIN")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/properties").with(user("tenant").roles("TENANT"))
                .contentType(APPLICATION_JSON).content(body("WHOLE", "[{\"roomNumber\":\"A\"}]"))).andExpect(status().isForbidden());
    }

    @Test
    void roomCategoriesArePrivateToTheLandlordAndNotAnAdminRoomCatalog() throws Exception {
        String created = create(body("BY_ROOM", "[{\"roomNumber\":\"OWNER-ROOM\"}]"));
        String ownRoomId = JsonPath.read(created, "$.data.rooms[0].id");
        mvc.perform(post("/api/properties").with(user("other").roles("LANDLORD"))
                .contentType(APPLICATION_JSON).content(body("BY_ROOM", "[{\"roomNumber\":\"OTHER-ROOM\"}]")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/room-types/categories").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        String response = mvc.perform(get("/api/room-types/categories").with(user("owner").roles("LANDLORD")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.util.List<String> ids = JsonPath.read(response, "$.data[*].rooms[*].id");
        assertEquals(java.util.List.of(ownRoomId), ids);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {PropertyRepository.class, RoomRepository.class, ProvinceRepository.class, UserRepository.class})
    @Import({com.tamdaptit.rentalmanagement.room.controller.RoomTypeController.class,
            com.tamdaptit.rentalmanagement.room.service.impl.RoomTypeServiceImpl.class,
            PropertyController.class, PropertyTypeController.class, PropertyServiceImpl.class,
            PropertyTypeServiceImpl.class, RoomController.class, RoomServiceImpl.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().authenticated()).build();
        }
        @Bean PropertyMapper propertyMapper() { return Mappers.getMapper(PropertyMapper.class); }
        @Bean PropertyTypeMapper propertyTypeMapper() { return Mappers.getMapper(PropertyTypeMapper.class); }
        @Bean com.tamdaptit.rentalmanagement.room.mapper.RoomTypeMapper roomTypeMapper() {
            return Mappers.getMapper(com.tamdaptit.rentalmanagement.room.mapper.RoomTypeMapper.class);
        }
        @Bean RoomMapper roomMapper() { return Mappers.getMapper(RoomMapper.class); }
        @Bean ImageStorageService storage() { return org.mockito.Mockito.mock(ImageStorageService.class); }
        @Bean DataSource dataSource() { return new DriverManagerDataSource(url(schema), dbUser(), password()); }
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var f = new LocalContainerEntityManagerFactoryBean();
            f.setDataSource(source); f.setPackagesToScan("com.tamdaptit.rentalmanagement");
            f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
            return f;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory f) { return new JpaTransactionManager(f); }
    }
}
