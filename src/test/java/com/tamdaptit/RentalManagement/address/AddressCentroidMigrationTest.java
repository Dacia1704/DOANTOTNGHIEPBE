package com.tamdaptit.rentalmanagement.address;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SUBSCRIPTION_TEST_MYSQL_URL",matches="jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/")
class AddressCentroidMigrationTest {
    @Test void seedUsesSyncedIdsRatherThanReplacingAdministrativeUnits() throws Exception {
        String schema="centroid_test_"+UUID.randomUUID().toString().replace("-","");
        String base=System.getenv("SUBSCRIPTION_TEST_MYSQL_URL");
        String user=System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_USER","root");
        String pass=System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_PASSWORD","");
        String options="?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh";
        try(var admin=DriverManager.getConnection(base+options,user,pass);var command=admin.createStatement()) {
            command.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            try {
                String url=base+schema+options;
                Flyway.configure().dataSource(url,user,pass).target("1").load().migrate();
                try(var c=DriverManager.getConnection(url,user,pass);var s=c.createStatement()) {
                    s.execute("INSERT INTO provinces(id,code,name,centroid_lat,centroid_lng) VALUES('synced-province','01','Thành phố Hà Nội',21.02,105.83)");
                    s.execute("INSERT INTO wards(id,code,name,province_id,centroid_lat,centroid_lng) VALUES('synced-ward','00166','Phường Cầu Giấy','synced-province',21.03,105.79)");
                    assertThrows(SQLException.class,()->s.execute("UPDATE wards SET centroid_lng=NULL WHERE id='synced-ward'"));
                    assertEquals(1,Flyway.configure().dataSource(url,user,pass).load().migrate().migrationsExecuted);
                    try(var r=s.executeQuery("SELECT id,centroid_lat FROM wards WHERE code='00166'")) {
                        assertTrue(r.next());assertEquals("synced-ward",r.getString(1));assertEquals(21.03,r.getDouble(2),0.000001);
                    }
                    try(var r=s.executeQuery("SELECT COUNT(*) FROM properties WHERE ward_id='synced-ward'")) {assertTrue(r.next());assertTrue(r.getInt(1)>0);}
                }
            } finally { if(schema.matches("centroid_test_[a-f0-9]{32}"))command.execute("DROP DATABASE `"+schema+"`"); }
        }
    }
}
