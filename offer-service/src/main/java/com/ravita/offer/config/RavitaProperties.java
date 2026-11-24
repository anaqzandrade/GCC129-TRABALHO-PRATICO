package com.ravita.offer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configurações próprias do serviço (prefixo "ravita" no application.yml).
 */
@ConfigurationProperties(prefix = "ravita")
public record RavitaProperties(String timeZone, Services services) {

    public record Services(String organizationUrl, Duration organizationTimeout) {
    }
}
