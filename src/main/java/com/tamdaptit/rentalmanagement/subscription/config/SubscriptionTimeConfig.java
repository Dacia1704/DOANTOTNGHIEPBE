package com.tamdaptit.rentalmanagement.subscription.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SubscriptionTimeConfig {
    @Bean
    Clock subscriptionClock() {
        return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
