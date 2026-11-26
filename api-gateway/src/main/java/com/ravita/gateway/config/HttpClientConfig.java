package com.ravita.gateway.config;

import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache de DNS curto no cliente HTTP que o gateway usa para chamar os BFFs.
 *
 * O DNS embutido do Docker responde com TTL de 600 s, e o resolvedor do Reactor Netty respeita esse TTL.
 * Sem este ajuste, quando um BFF é recriado e ganha outro IP, o gateway continua mandando as requisições
 * para o IP antigo por até 10 minutos e responde 503, mesmo com o BFF saudável. Com no máximo 5 s de cache,
 * o gateway acompanha recriações e réplicas.
 */
@Configuration
public class HttpClientConfig {

    static final Duration MAX_DNS_CACHE = Duration.ofSeconds(5);
    static final Duration NEGATIVE_DNS_CACHE = Duration.ofSeconds(1);

    @Bean
    HttpClientCustomizer shortDnsCache() {
        return httpClient -> httpClient.resolver(spec -> spec
                .cacheMaxTimeToLive(MAX_DNS_CACHE)
                .cacheNegativeTimeToLive(NEGATIVE_DNS_CACHE));
    }
}
