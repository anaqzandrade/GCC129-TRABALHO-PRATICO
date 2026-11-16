package com.ravita.offer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {

    /** Relógio em UTC: toda regra de negócio compara instantes em UTC. Nos testes ele é substituído. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Fuso usado apenas para escrever datas nas respostas. */
    @Bean
    public ZoneId responseZone(RavitaProperties properties) {
        String zone = properties.timeZone();
        return zone == null || zone.isBlank() ? ZoneId.of("America/Sao_Paulo") : ZoneId.of(zone);
    }
}
