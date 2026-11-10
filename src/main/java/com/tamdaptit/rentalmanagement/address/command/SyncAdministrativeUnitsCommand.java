package com.tamdaptit.rentalmanagement.address.command;

import com.tamdaptit.rentalmanagement.address.service.AddressImporter;
import com.tamdaptit.rentalmanagement.address.service.AddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(name = "app.address-sync.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SyncAdministrativeUnitsCommand implements ApplicationRunner {
    private final AddressService client;
    private final AddressImporter importer;
    private final ConfigurableApplicationContext context;
    private final ObjectMapper mapper;
    private final org.flywaydb.core.Flyway flyway;
    @Value("${app.address-sync.snapshot-file:}")
    private String snapshotFile;
    @Value("${app.address-sync.require-schema-version:}")
    private String requireSchemaVersion;
    @Value("${app.address-sync.skip-if-seeded:false}")
    private boolean skipIfSeeded;
    @Value("${app.address-sync.setup-state-file:}")
    private String setupStateFile;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        boolean seedApplied = java.util.Arrays.stream(flyway.info().applied())
                .anyMatch(migration -> migration.getVersion() != null
                        && "2".equals(migration.getVersion().getVersion())
                        && migration.getState() == org.flywaydb.core.api.MigrationState.SUCCESS);
        if (skipIfSeeded && seedApplied) {
            log.info("V2 demo seed is already applied; preserving existing data and seed files. Continuing with remaining migrations.");
            writeSetupState(true);
            System.exit(org.springframework.boot.SpringApplication.exit(context, () -> 0));
            return;
        }
        if (!requireSchemaVersion.isBlank()) {
            var current = flyway.info().current();
            if (current == null || !requireSchemaVersion.equals(current.getVersion().getVersion())) {
                throw new IllegalStateException("Demo setup requires a fresh schema at V" + requireSchemaVersion
                        + "; do not regenerate an already applied V2.");
            }
        }
        log.info("Fetching Vietnamese administrative units...");
        var snapshot = client.fetch();
        var result = importer.apply(snapshot);
        if (!snapshotFile.isBlank()) {
            var output = Path.of(snapshotFile).toAbsolutePath();
            Files.createDirectories(output.getParent());
            Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot));
            log.info("Administrative snapshot exported to {}", output);
        }
        log.info("Administrative sync completed: {}", result);
        writeSetupState(false);
        System.exit(org.springframework.boot.SpringApplication.exit(context, () -> 0));
    }

    private void writeSetupState(boolean seedApplied) throws Exception {
        if (setupStateFile.isBlank()) return;
        var output = Path.of(setupStateFile).toAbsolutePath();
        Files.createDirectories(output.getParent());
        Files.writeString(output, mapper.writeValueAsString(java.util.Map.of("seedApplied", seedApplied)));
    }
}
