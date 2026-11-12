package com.tamdaptit.rentalmanagement.address.command;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/** One-shot migration process used by scripts/setup-demo.ps1. */
@Component
@ConditionalOnProperty(name = "app.database-setup.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DatabaseSetupCommand implements ApplicationRunner {
    private final ConfigurableApplicationContext context;

    @Override public void run(ApplicationArguments args) {
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
