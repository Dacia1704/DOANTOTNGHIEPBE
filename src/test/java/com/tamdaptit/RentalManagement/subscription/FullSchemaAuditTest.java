package com.tamdaptit.rentalmanagement.subscription;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Review the full SQL build against every current JPA entity using isolated schemas only. */
@EnabledIfEnvironmentVariable(named = "SUBSCRIPTION_TEST_MYSQL_URL", matches = "jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/")
class FullSchemaAuditTest {
    private final String prefix = "schema_audit_" + UUID.randomUUID().toString().replace("-", "");
    private String user() { return System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_USER", "root"); }
    private String password() { return System.getenv().getOrDefault("SUBSCRIPTION_TEST_MYSQL_PASSWORD", ""); }
    private String url(String schema) { return System.getenv("SUBSCRIPTION_TEST_MYSQL_URL") + schema + "?serverTimezone=Asia/Ho_Chi_Minh&useSSL=false&allowPublicKeyRetrieval=true"; }
    private Connection connect(String schema) throws SQLException { return DriverManager.getConnection(url(schema), user(), password()); }
    private LocalContainerEntityManagerFactoryBean factory(String schema, String action) {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(new DriverManagerDataSource(url(schema), user(), password()));
        factory.setPackagesToScan("com.tamdaptit.rentalmanagement");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", action, "hibernate.hbm2ddl.halt_on_error", "true"));
        return factory;
    }
    private List<Map<String,String>> rows(Connection c, String sql) throws SQLException {
        List<Map<String,String>> result = new ArrayList<>();
        try(var statement=c.createStatement();var rs=statement.executeQuery(sql)) {
            while(rs.next()) { Map<String,String> row=new LinkedHashMap<>();for(int i=1;i<=rs.getMetaData().getColumnCount();i++)row.put(rs.getMetaData().getColumnLabel(i),rs.getString(i));result.add(row); }
        }
        return result;
    }
    private Map<String, Map<String,String>> columns(Connection c) throws SQLException {
        Map<String,Map<String,String>> result = new TreeMap<>();
        for(var row:rows(c,"SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE,COLUMN_TYPE,IS_NULLABLE,CHARACTER_MAXIMUM_LENGTH,NUMERIC_PRECISION,NUMERIC_SCALE FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION"))result.put(row.get("TABLE_NAME")+"."+row.get("COLUMN_NAME"),row);
        return result;
    }
    private Set<String> constraints(Connection c, String kind) throws SQLException {
        Set<String> result=new TreeSet<>();
        String sql=kind.equals("FK")
            ? "SELECT CONCAT(TABLE_NAME,'.',COLUMN_NAME,' -> ',REFERENCED_TABLE_NAME,'.',REFERENCED_COLUMN_NAME) AS edge FROM information_schema.key_column_usage WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL"
            : "SELECT CONCAT(k.TABLE_NAME,':',GROUP_CONCAT(k.COLUMN_NAME ORDER BY k.ORDINAL_POSITION)) AS edge FROM information_schema.key_column_usage k JOIN information_schema.table_constraints t ON t.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND t.TABLE_NAME=k.TABLE_NAME AND t.CONSTRAINT_NAME=k.CONSTRAINT_NAME WHERE k.TABLE_SCHEMA=DATABASE() AND t.CONSTRAINT_TYPE='"+kind+"' GROUP BY k.TABLE_NAME,k.CONSTRAINT_NAME";
        for(var row:rows(c,sql))result.add(row.get("edge"));return result;
    }
    private String family(String type) {
        return switch(type) { case "enum","varchar","char" -> "string";case "bit","tinyint" -> "boolean";default -> type; };
    }
    private List<String> compare(Connection actual, Connection expected) throws SQLException {
        List<String> issues = new ArrayList<>();var ac=columns(actual);var ex=columns(expected);
        for(var entry:ex.entrySet()) {
            String key=entry.getKey();var e=entry.getValue();var a=ac.get(key);
            if(a==null){issues.add("MISSING COLUMN "+key);continue;}
            if(!family(a.get("DATA_TYPE")).equals(family(e.get("DATA_TYPE"))))issues.add("TYPE "+key+" SQL="+a.get("COLUMN_TYPE")+" JPA="+e.get("COLUMN_TYPE"));
            if(!a.get("IS_NULLABLE").equals(e.get("IS_NULLABLE")))issues.add("NULLABLE "+key+" SQL="+a.get("IS_NULLABLE")+" JPA="+e.get("IS_NULLABLE"));
            if(a.get("DATA_TYPE").equals("varchar")&&e.get("DATA_TYPE").equals("varchar")&&!Objects.equals(a.get("CHARACTER_MAXIMUM_LENGTH"),e.get("CHARACTER_MAXIMUM_LENGTH")))issues.add("LENGTH "+key+" SQL="+a.get("CHARACTER_MAXIMUM_LENGTH")+" JPA="+e.get("CHARACTER_MAXIMUM_LENGTH"));
            if(a.get("DATA_TYPE").equals("decimal")&&(!Objects.equals(a.get("NUMERIC_PRECISION"),e.get("NUMERIC_PRECISION"))||!Objects.equals(a.get("NUMERIC_SCALE"),e.get("NUMERIC_SCALE"))))issues.add("DECIMAL "+key+" SQL="+a.get("COLUMN_TYPE")+" JPA="+e.get("COLUMN_TYPE"));
            if(a.get("DATA_TYPE").equals("enum")&&e.get("DATA_TYPE").equals("enum")) {
                var av=new TreeSet<>(List.of(a.get("COLUMN_TYPE").substring(5,a.get("COLUMN_TYPE").length()-1).split(",")));var ev=new TreeSet<>(List.of(e.get("COLUMN_TYPE").substring(5,e.get("COLUMN_TYPE").length()-1).split(",")));
                if(!av.equals(ev))issues.add("ENUM "+key+" SQL="+av+" JPA="+ev);
            }
        }
        for(String key:ac.keySet())if(!ex.containsKey(key))issues.add("EXTRA COLUMN "+key);
        for(String kind:List.of("FK","PRIMARY KEY","UNIQUE")){var a=constraints(actual,kind);var e=constraints(expected,kind);for(String edge:e)if(!a.contains(edge))issues.add("MISSING "+kind+" "+edge);for(String edge:a)if(!e.contains(edge))issues.add("EXTRA "+kind+" "+edge);}
        return issues;
    }
    private Set<String> foreignKeyRules(Connection c) throws SQLException {
        Set<String> rules=new TreeSet<>();
        for(var row:rows(c,"SELECT CONCAT(k.TABLE_NAME,'.',k.COLUMN_NAME,' -> ',k.REFERENCED_TABLE_NAME,'.',k.REFERENCED_COLUMN_NAME,' DELETE ',r.DELETE_RULE,' UPDATE ',r.UPDATE_RULE) AS rule FROM information_schema.key_column_usage k JOIN information_schema.referential_constraints r ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.TABLE_NAME=k.TABLE_NAME AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME WHERE k.TABLE_SCHEMA=DATABASE()"))rules.add(row.get("rule"));
        return rules;
    }
    private Set<String> checks(Connection c) throws SQLException {
        Set<String> result=new TreeSet<>();
        for(var row:rows(c,"SELECT CONCAT(t.TABLE_NAME,':',c.CHECK_CLAUSE) AS rule FROM information_schema.check_constraints c JOIN information_schema.table_constraints t ON t.CONSTRAINT_SCHEMA=c.CONSTRAINT_SCHEMA AND t.CONSTRAINT_NAME=c.CONSTRAINT_NAME WHERE c.CONSTRAINT_SCHEMA=DATABASE()"))result.add(row.get("rule"));
        return result;
    }
    private Set<String> indexes(Connection c) throws SQLException {
        Set<String> result=new TreeSet<>();
        for(var row:rows(c,"SELECT CONCAT(TABLE_NAME,':',GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)) AS idx FROM information_schema.statistics WHERE TABLE_SCHEMA=DATABASE() GROUP BY TABLE_NAME,INDEX_NAME"))result.add(row.get("idx"));
        return result;
    }
    private void execute(Connection c,String sql) throws SQLException { try(var statement=c.createStatement()){statement.execute(sql);} }
    private void rejects(Connection c,String sql,int errorCode) {
        SQLException error=assertThrows(SQLException.class,()->execute(c,sql));assertEquals(errorCode,error.getErrorCode(),error.getMessage());
    }
    private void verifyDataGuards(Connection c) throws SQLException {
        execute(c,"INSERT INTO users(id,email,password) VALUES ('owner','full-schema@example.test','test')");
        execute(c,"INSERT INTO provinces(id,code,name) VALUES ('province','AUDIT-P','Test')");
        execute(c,"INSERT INTO wards(id,province_id,code,name) VALUES ('ward','province','AUDIT-W','Test')");
        execute(c,"INSERT INTO property_types(id,name) VALUES ('type','Test')");
        execute(c,"INSERT INTO properties(id,landlord_id,property_type_id,name,rental_mode,province_id,ward_id,address_line) VALUES ('property','owner','type','Test','BY_ROOM','province','ward','Test address')");
        rejects(c,"INSERT INTO listings(id,property_id,title,status) VALUES ('invalid','missing','Invalid FK','HIDDEN')",1452);
        execute(c,"INSERT INTO listings(id,property_id,title,slug,status) VALUES ('post-1','property','Test 1','same-slug','HIDDEN')");
        rejects(c,"INSERT INTO listings(id,property_id,title,slug,status) VALUES ('post-2','property','Test 2','same-slug','HIDDEN')",1062);
        execute(c,"INSERT INTO listings(id,property_id,title,status) VALUES ('post-3','property','No slug 1','HIDDEN'),('post-4','property','No slug 2','HIDDEN')");
        execute(c,"INSERT INTO room_types(id,name) VALUES ('room-type','Test')");
        execute(c,"INSERT INTO rooms(id,property_id,room_number,room_type_id,price,area_m2) VALUES ('room','property','A101','room-type',100,20)");
        rejects(c,"INSERT INTO rooms(id,property_id,room_number,room_type_id,price,area_m2) VALUES ('duplicate','property','A101','room-type',100,20)",1062);
        execute(c,"INSERT INTO service_package_subscriptions(id,landlord_id,package_id,start_at,end_at,status) SELECT 'sub-1','owner',id,'2026-01-01','2026-02-01','ACTIVE' FROM service_packages WHERE code='FREE'");
        rejects(c,"INSERT INTO service_package_subscriptions(id,landlord_id,package_id,start_at,end_at,status) SELECT 'sub-2','owner',id,'2026-01-01','2026-02-01','ACTIVE' FROM service_packages WHERE code='FREE'",1062);
        rejects(c,"UPDATE properties SET rental_mode='INVALID' WHERE id='property'",3819);
    }
    @Test
    void auditEveryEntityAgainstFullBuild() throws Exception {
        List<String> schemas=List.of(prefix+"_actual",prefix+"_expected",prefix+"_full");
        try(Connection admin=connect("")) {
            for(String schema:schemas)try(var st=admin.createStatement()){st.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");}
            try(Connection actual=connect(schemas.get(0));Connection expected=connect(schemas.get(1))) {
                ScriptUtils.executeSqlScript(actual,new FileSystemResource("src/main/resources/db/migration/V12__fullsql.sql"));
                ScriptUtils.executeSqlScript(actual,new FileSystemResource("src/main/resources/db/migration/V13__listing_belongs_to_property.sql"));
                var generated=factory(schemas.get(1),"create-only");try{generated.afterPropertiesSet();}finally{generated.destroy();}
                var issues=compare(actual,expected);
                Path output=Path.of(System.getProperty("java.io.tmpdir"),"rental-schema-audit.txt");Files.write(output,issues);
                StringBuilder ddl=new StringBuilder();for(var row:rows(actual,"SELECT TABLE_NAME FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY TABLE_NAME")){String name=row.get("TABLE_NAME");try(var st=actual.createStatement();var rs=st.executeQuery("SHOW CREATE TABLE `"+name+"`")){rs.next();ddl.append(rs.getString(2)).append(";\n\n");}}
                Files.writeString(Path.of(System.getProperty("java.io.tmpdir"),"rental-schema-actual.sql"),ddl);
                System.out.println("SCHEMA AUDIT: tables="+rows(actual,"SELECT TABLE_NAME FROM information_schema.tables WHERE table_schema=DATABASE()").size()+", columns="+columns(actual).size()+", FKs="+constraints(actual,"FK").size()+", differences="+issues.size()+"; report="+output);
                for(String issue:issues)System.out.println(issue);
                assertTrue(issues.contains("MISSING UNIQUE listings:slug"), "Pin the mismatch in the unchanged V12 + V13 build");
                var validate=factory(schemas.get(0),"validate");try{validate.afterPropertiesSet();}finally{validate.destroy();}
                try(Connection full=connect(schemas.get(2))) {
                    ScriptUtils.executeSqlScript(full,new FileSystemResource("sql/V14__fullsql.sql"));
                    var fullIssues=compare(full,expected);
                    Set<String> allowedSqlOnlyGuards=Set.of(
                        "EXTRA COLUMN service_package_subscriptions.active_landlord_id",
                        "EXTRA COLUMN service_package_subscriptions.scheduled_landlord_id",
                        "EXTRA COLUMN service_subscription_orders.open_change_landlord_id",
                        "EXTRA UNIQUE amenities:name", "EXTRA UNIQUE listing_daily_stats:listing_id,stat_date",
                        "EXTRA UNIQUE payments:payment_method,provider_txn_id", "EXTRA UNIQUE room_types:name",
                        "EXTRA UNIQUE saved_listings:tenant_id,listing_id", "EXTRA UNIQUE service_package_prices:package_id,duration_months",
                        "EXTRA UNIQUE service_package_subscriptions:active_landlord_id", "EXTRA UNIQUE service_package_subscriptions:scheduled_landlord_id",
                        "EXTRA UNIQUE service_subscription_orders:open_change_landlord_id", "EXTRA UNIQUE service_subscription_periods:subscription_id,period_number",
                        "EXTRA UNIQUE users:citizen_id", "EXTRA UNIQUE users:phone");
                    assertEquals(allowedSqlOnlyGuards,new TreeSet<>(fullIssues),String.join("\n",fullIssues));
                    assertEquals(44,rows(full,"SELECT TABLE_NAME FROM information_schema.tables WHERE table_schema=DATABASE()").size());
                    assertEquals(85,constraints(full,"FK").size());
                    assertEquals(foreignKeyRules(actual),foreignKeyRules(full),"Preserve every FK target and delete/update action");
                    assertEquals(checks(actual),checks(full),"Preserve all existing SQL business checks");
                    for(var index:indexes(expected))assertTrue(indexes(full).stream().anyMatch(existing -> existing.equals(index)||existing.startsWith(index+",")),"Missing index coverage: "+index);
                    assertTrue(columns(full).keySet().stream().noneMatch(key -> key.contains(".legacy_")));
                    assertEquals("3",rows(full,"SELECT max_listings FROM service_packages WHERE code='FREE'").getFirst().get("max_listings"));
                    var fullValidate=factory(schemas.get(2),"validate");
                    try { fullValidate.afterPropertiesSet(); assertEquals(44,fullValidate.getObject().getMetamodel().getEntities().size()); }
                    finally { fullValidate.destroy(); }
                    verifyDataGuards(full);
                    System.out.println("V14 VERIFIED: 44 entity tables, "+columns(full).size()+" columns, 85 FK relationships, all mapped indexes, all inherited business checks, slug uniqueness, generated subscription guard; Hibernate validate passed.");
                }
            }
        }finally{try(Connection admin=connect("")){for(String schema:schemas)if(schema.matches("schema_audit_[a-f0-9]{32}_(actual|expected|full)"))try(var st=admin.createStatement()){st.execute("DROP DATABASE IF EXISTS `"+schema+"`");}}}
    }
}


