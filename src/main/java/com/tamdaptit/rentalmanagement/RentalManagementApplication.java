package com.tamdaptit.rentalmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RentalManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(RentalManagementApplication.class, args);
    }
}
