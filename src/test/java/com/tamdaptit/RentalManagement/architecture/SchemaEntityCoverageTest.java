package com.tamdaptit.rentalmanagement.architecture;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Every application table has a JPA mapping or an explicitly documented JDBC responsibility. */
class SchemaEntityCoverageTest {
    private static final Map<String,String> JDBC_TABLES = Map.of(
            "rental_events", "rental/service/RentalEvents.java",
            "rental_pdf_assets", "rental/service/RentalPdfJobs.java",
            "notification_outbox", "notification/service/NotificationPublisher.java");

    @Test
    void migrationsAndEntitiesAgreeExceptForDocumentedInfrastructureTables() throws Exception {
        var tables = new TreeSet<String>();
        var create = Pattern.compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?(\\w+)",Pattern.CASE_INSENSITIVE);
        try (var migrations = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (var migration : migrations.filter(p -> p.toString().endsWith(".sql")).toList()) {
                var matcher = create.matcher(Files.readString(migration));
                while (matcher.find()) tables.add(matcher.group(1));
            }
        }
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        var mapped = new TreeSet<String>();
        for (var definition : scanner.findCandidateComponents("com.tamdaptit.rentalmanagement")) {
            var entity = Class.forName(definition.getBeanClassName());
            var table = entity.getAnnotation(Table.class);
            assertNotNull(table,entity.getName()+" needs an explicit table name");
            assertTrue(mapped.add(table.name()),"Duplicate entity mapping for "+table.name());
        }
        assertFalse(mapped.isEmpty());
        var missingTables = new TreeSet<>(mapped);
        missingTables.removeAll(tables);
        assertTrue(missingTables.isEmpty(),"Entities without migrations: "+missingTables);
        var unmapped = new TreeSet<>(tables);
        unmapped.removeAll(mapped);
        assertEquals(JDBC_TABLES.keySet(),unmapped,
                "Each table without an entity needs a documented JDBC owner");
        for (var owner : JDBC_TABLES.entrySet()) {
            String source = Files.readString(Path.of("src/main/java/com/tamdaptit/rentalmanagement",owner.getValue()));
            assertTrue(source.contains(owner.getKey()),"JDBC table has no owner: "+owner.getKey());
        }
    }
}
