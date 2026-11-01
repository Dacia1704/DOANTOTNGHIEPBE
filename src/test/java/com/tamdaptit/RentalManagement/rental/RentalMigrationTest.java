package com.tamdaptit.rentalmanagement.rental;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SUBSCRIPTION_TEST_MYSQL_URL",matches="jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/")
class RentalMigrationTest {
    @Test void legacyConflictsFailBeforeAlteringTablesAndPreserveHistory() throws Exception {
        String schema="rental_migration_"+UUID.randomUUID().toString().replace("-","");
        String base=System.getenv("SUBSCRIPTION_TEST_MYSQL_URL"),user=System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_USER","root"),password=System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_PASSWORD","");
        try(var admin=DriverManager.getConnection(base+"?allowPublicKeyRetrieval=true&useSSL=false",user,password);var command=admin.createStatement()) {
            command.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");
            try {
                String url=base+schema+"?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Ho_Chi_Minh";
                Flyway.configure().dataSource(url,user,password).target("2").load().migrate();
                try(var db=DriverManager.getConnection(url,user,password);var s=db.createStatement()) {
                    for(String id:new String[]{"legacy-a","legacy-b"}) s.execute("""
                        INSERT INTO rental_agreements(id,agreement_code,room_id,tenant_id,landlord_id,price,start_date,end_date,status,created_by_role)
                        SELECT '%s','%s',id,(SELECT id FROM users ORDER BY id LIMIT 1),(SELECT id FROM users ORDER BY id LIMIT 1),100,'2026-01-01','2027-01-01','DRAFT','LANDLORD' FROM rooms ORDER BY id LIMIT 1
                        """.formatted(id,id));
                    var flyway=Flyway.configure().dataSource(url,user,password).load();
                    assertThrows(FlywayException.class,flyway::migrate);
                    try(var result=s.executeQuery("SELECT COUNT(*) FROM rental_agreements WHERE id LIKE 'legacy-%'")){result.next();assertEquals(2,result.getInt(1));}
                    try(var result=s.executeQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rental_requests' AND column_name='room_id'")){result.next();assertEquals(0,result.getInt(1));}
                    // Explicit operator resolution in this fixture, not an automatic migration rewrite.
                    s.execute("UPDATE rental_agreements SET status='CANCELLED' WHERE id='legacy-b'");
                    flyway.repair();flyway.migrate();flyway.validate();
                    try(var result=s.executeQuery("SELECT COUNT(*) FROM rental_agreements WHERE id LIKE 'legacy-%'")){result.next();assertEquals(2,result.getInt(1));}
                }
            } finally { if(schema.matches("rental_migration_[a-f0-9]{32}"))command.execute("DROP DATABASE `"+schema+"`"); }
        }
    }
}
