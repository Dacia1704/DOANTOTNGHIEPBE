package com.tamdaptit.rentalmanagement.subscription;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Current V1 guards replace assertions about the retired V3-V13 upgrade chain. */
@EnabledIfEnvironmentVariable(named="SUBSCRIPTION_TEST_MYSQL_URL",matches="jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/")
class SubscriptionSchemaMigrationTest {
    @Test void consolidatedSchemaKeepsSubscriptionUniquenessAndPaymentChecks() throws Exception {
        String schema="billing_schema_"+UUID.randomUUID().toString().replace("-","");
        String base=System.getenv("SUBSCRIPTION_TEST_MYSQL_URL");
        String user=System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_USER","root");
        String pass=System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_PASSWORD","");
        String options="?serverTimezone=Asia/Ho_Chi_Minh&useSSL=false&allowPublicKeyRetrieval=true";
        try(var admin=DriverManager.getConnection(base+options,user,pass);var command=admin.createStatement()) {
            command.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");
            try {
                String url=base+schema+options;
                assertEquals(1,Flyway.configure().dataSource(url,user,pass).target("1").load().migrate().migrationsExecuted);
                try(var db=DriverManager.getConnection(url,user,pass);var sql=db.createStatement()) {
                    sql.execute("INSERT INTO users(id,email,password) VALUES('owner','billing-test@example.test','unused')");
                    sql.execute("INSERT INTO service_packages(id,code,name,max_listings) VALUES('p','PAID','Paid',15)");
                    String subscription="INSERT INTO service_package_subscriptions(id,landlord_id,package_id,start_at,end_at,status) VALUES('%s','owner','p','2026-01-01','2027-01-01','ACTIVE')";
                    sql.execute(subscription.formatted("one"));
                    assertEquals(1062,assertThrows(SQLException.class,()->sql.execute(subscription.formatted("two"))).getErrorCode());
                    sql.execute("UPDATE service_package_subscriptions SET status='EXPIRED' WHERE id='one'");
                    sql.execute(subscription.formatted("two"));
                    assertThrows(SQLException.class,()->sql.execute("UPDATE service_package_subscriptions SET end_at=start_at WHERE id='two'"));
                    assertThrows(SQLException.class,()->sql.execute("UPDATE service_packages SET high_priority_slots=-1 WHERE id='p'"));
                    assertThrows(SQLException.class,()->sql.execute("INSERT INTO payments(id,landlord_id,type,amount,payment_method,status,merchant_order_id,expires_at) VALUES('bad','owner','SERVICE_PACKAGE',100,'BANK_TRANSFER','PENDING','bad','2027-01-01')"));
                    try(var tables=sql.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")) {assertTrue(tables.next());assertEquals(50,tables.getInt(1));}
                }
            } finally {if(schema.matches("billing_schema_[a-f0-9]{32}"))command.execute("DROP DATABASE `"+schema+"`");}
        }
    }
}
