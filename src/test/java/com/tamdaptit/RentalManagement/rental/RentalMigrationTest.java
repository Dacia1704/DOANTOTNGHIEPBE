package com.tamdaptit.rentalmanagement.rental;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SUBSCRIPTION_TEST_MYSQL_URL",matches="jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/")
class RentalMigrationTest {
    @Test void schemaAlreadyGuardsDuplicateAgreementsAndRequestsAfterV1() throws Exception {
        String schema="rental_migration_"+UUID.randomUUID().toString().replace("-","");
        String base=System.getenv("SUBSCRIPTION_TEST_MYSQL_URL"), user=System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_USER","root"), password=System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_PASSWORD","");
        String options="?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Ho_Chi_Minh";
        try(var admin=DriverManager.getConnection(base+options,user,password);var command=admin.createStatement()) {
            command.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");
            try {
                String url=base+schema+options;
                assertEquals(1,Flyway.configure().dataSource(url,user,password).target("1").load().migrate().migrationsExecuted);
                try(var db=DriverManager.getConnection(url,user,password);var s=db.createStatement()) {
                    s.execute("INSERT INTO users(id,email,password) VALUES ('owner','migration-owner@gmail.com','unused'),('tenant','migration-tenant@gmail.com','unused')");
                    s.execute("INSERT INTO provinces(id,code,name) VALUES ('province','00','Demo')");
                    s.execute("INSERT INTO wards(id,province_id,code,name) VALUES ('ward','province','00000','Demo')");
                    s.execute("INSERT INTO property_types(id,name) VALUES ('type','Demo')");
                    s.execute("INSERT INTO properties(id,landlord_id,property_type_id,name,rental_mode,province_id,ward_id,address_line) VALUES ('property','owner','type','Demo','BY_ROOM','province','ward','Demo')");
                    s.execute("INSERT INTO room_types(id,name) VALUES ('room-type','Demo')");
                    s.execute("INSERT INTO rooms(id,property_id,room_type_id,room_number,price,area_m2) VALUES ('room','property','room-type','101',2000000,25)");
                    s.execute("INSERT INTO listings(id,property_id,title,status) VALUES ('listing','property','Demo','HIDDEN')");
                    String agreement="INSERT INTO rental_agreements(id,agreement_code,room_id,tenant_id,landlord_id,price,start_date,end_date,status,created_by_role) VALUES ('%s','%s','room','tenant','owner',2000000,CURRENT_DATE,DATE_ADD(CURRENT_DATE,INTERVAL 6 MONTH),'DRAFT','LANDLORD')";
                    s.execute(agreement.formatted("a","CODE-A"));
                    assertEquals(1062,assertThrows(SQLException.class,()->s.execute(agreement.formatted("b","CODE-B"))).getErrorCode());
                    s.execute("UPDATE rental_agreements SET status='CANCELLED' WHERE id='a'");
                    s.execute(agreement.formatted("b","CODE-B"));
                    String request="INSERT INTO rental_requests(id,listing_id,room_id,tenant_id,landlord_id,status,duration_months,occupant_count) VALUES ('%s','listing','room','tenant','owner','PENDING',6,2)";
                    s.execute(request.formatted("request-a"));
                    assertEquals(1062,assertThrows(SQLException.class,()->s.execute(request.formatted("request-b"))).getErrorCode());
                    s.execute("UPDATE rental_requests SET status='CANCELLED' WHERE id='request-a'");
                    s.execute(request.formatted("request-b"));
                }
            } finally {
                if(schema.matches("rental_migration_[a-f0-9]{32}")) command.execute("DROP DATABASE IF EXISTS `"+schema+"`");
            }
        }
    }
}
