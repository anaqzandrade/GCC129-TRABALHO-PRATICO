package com.ravita.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Configurações próprias do gateway (prefixo "ravita" no application.yml). */
@ConfigurationProperties(prefix = "ravita")
public record GatewayProperties(Jwt jwt, RateLimit rateLimit) {

    /** secret: chave HS256, com pelo menos 32 caracteres; vem de JWT_SECRET. */
    public record Jwt(String secret, String issuer, Duration ttl) {
    }

    /** Token bucket por cliente: capacity requisições, recarregadas aos poucos ao longo de refillPeriod. */
    public record RateLimit(int capacity, Duration refillPeriod) {
    }
}
