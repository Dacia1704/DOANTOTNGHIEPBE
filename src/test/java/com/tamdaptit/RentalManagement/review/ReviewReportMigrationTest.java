package com.tamdaptit.rentalmanagement.review;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SUBSCRIPTION_TEST_MYSQL_URL",matches="jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/")
class ReviewReportMigrationTest {
    @Test void freshSchemaPreservesReviewHistoryAndRejectsNullableVisibility() throws Exception {
        String schema="review_upgrade_"+UUID.randomUUID().toString().replace("-","");
        String user=System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_USER","root");
        String password=System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_PASSWORD","");
        String server=System.getenv("SUBSCRIPTION_TEST_MYSQL_URL");
        String suffix="?serverTimezone=Asia/Ho_Chi_Minh&useSSL=false&allowPublicKeyRetrieval=true";
        try(var admin=DriverManager.getConnection(server+suffix,user,password);var command=admin.createStatement()) {
            command.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");
            try {
                String url=server+schema+suffix;
                Flyway.configure().dataSource(url,user,password).target("2").load().migrate();
                try(var db=DriverManager.getConnection(url,user,password);var sql=db.createStatement()) {
                    sql.execute("""
                        INSERT INTO rental_agreements(id,agreement_code,room_id,tenant_id,landlord_id,price,start_date,end_date,status,created_by_role)
                        SELECT 'legacy-agreement','legacy-agreement',r.id,
                            (SELECT ur.user_id FROM user_roles ur JOIN roles role ON role.id=ur.role_id WHERE role.code='TENANT' LIMIT 1),
                            p.landlord_id,3000000,'2026-01-01','2026-02-01','EXPIRED','LANDLORD'
                        FROM rooms r JOIN properties p ON p.id=r.property_id LIMIT 1
                        """);
                    sql.execute("""
                        INSERT INTO room_reviews(id,rental_agreement_id,tenant_id,room_id,rating,comment,is_hidden)
                        SELECT 'legacy-review',id,tenant_id,room_id,4,'Legacy content',0
                        FROM rental_agreements WHERE id='legacy-agreement'
                        """);
                    var flyway=Flyway.configure().dataSource(url,user,password).load();
                    assertEquals(0,flyway.migrate().migrationsExecuted);
                    flyway.validate();assertEquals(0,flyway.migrate().migrationsExecuted);
                    try(var result=sql.executeQuery("SELECT rating,comment,is_hidden,hidden_reason FROM room_reviews WHERE id='legacy-review'")) {
                        assertTrue(result.next());assertEquals(4,result.getInt(1));assertEquals("Legacy content",result.getString(2));
                        assertFalse(result.getBoolean(3));assertFalse(result.wasNull());assertNull(result.getString(4));
                    }
                    assertThrows(SQLException.class,()->sql.execute("UPDATE room_reviews SET is_hidden=NULL WHERE id='legacy-review'"));
                    assertThrows(SQLException.class,()->sql.execute("DELETE FROM rental_agreements WHERE id='legacy-agreement'"));
                }
            } finally {
                if(schema.matches("review_upgrade_[a-f0-9]{32}"))command.execute("DROP DATABASE `"+schema+"`");
            }
        }
    }
}
