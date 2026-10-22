package com.tamdaptit.rentalmanagement.command;

import com.tamdaptit.rentalmanagement.service.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.address-sync.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SyncAdministrativeUnitsCommand implements ApplicationRunner {
    private final AddressService client;
    private final AddressImporter importer;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Fetching Vietnamese administrative units...");
        var result = importer.apply(client.fetch());
        log.info("Administrative sync completed: {}", result);
        System.exit(org.springframework.boot.SpringApplication.exit(context, () -> 0));
    }
}
