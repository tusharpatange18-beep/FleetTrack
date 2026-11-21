package com.fleettrack.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(FleetProperties.class)
public class AppConfig {

    /** Injected wherever "now" matters, so tests can control time. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
