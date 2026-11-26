package com.ravita.gateway.filter;

import com.ravita.gateway.config.GatewayProperties;
import com.ravita.gateway.error.JsonErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting por cliente (doc 02, seção 10), com token bucket em memória: cada cliente tem um balde
 * de capacity fichas, que se recarrega aos poucos ao longo de refillPeriod. Sem ficha, a resposta é 429.
 *
 * <p>O cliente é identificado pelo token (Authorization) ou, sem token, pelo IP. Roda antes da
 * autenticação, então também segura uma rajada de tokens inválidos. Em memória basta para uma instância
 * do gateway; com várias, o balde precisaria ficar num armazenamento compartilhado, como o Redis.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitWebFilter.class);
    private static final int MAX_CLIENTS = 10_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final long refillNanos;

    public RateLimitWebFilter(GatewayProperties properties) {
        this.capacity = properties.rateLimit().capacity();
        this.refillNanos = properties.rateLimit().refillPeriod().toNanos();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        // health e arquivos estáticos das páginas não contam: abrir uma página carrega vários arquivos
        if (path.startsWith("/actuator") || path.startsWith("/app/") || "/".equals(path)) {
            return chain.filter(exchange);
        }
        if (buckets.size() > MAX_CLIENTS) {
            buckets.clear();
        }
        String client = clientKey(exchange.getRequest());
        Bucket bucket = buckets.computeIfAbsent(client, key -> new Bucket(capacity, System.nanoTime()));
        long remaining = bucket.tryConsume(System.nanoTime(), capacity, refillNanos);
        if (remaining < 0) {
            long retryAfterSeconds = Math.max(1, refillNanos / capacity / 1_000_000_000L);
            exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
            exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", "0");
            log.warn("Rate limit atingido para {} em {}", client.startsWith("ip:") ? client : "token", path);
            return JsonErrors.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Muitas requisições. Tente de novo em alguns segundos.");
        }
        exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", String.valueOf(remaining));
        return chain.filter(exchange);
    }

    private static String clientKey(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && !authorization.isBlank()) {
            // o hash basta para separar os clientes, sem guardar o token em memória
            return "token:" + Integer.toHexString(authorization.hashCode());
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return "ip:" + (remote == null || remote.getAddress() == null ? "desconhecido" : remote.getAddress().getHostAddress());
    }

    /** Balde de um cliente. As fichas voltam proporcionalmente ao tempo passado desde a última requisição. */
    private static final class Bucket {
        private double tokens;
        private long lastRefill;

        Bucket(int capacity, long now) {
            this.tokens = capacity;
            this.lastRefill = now;
        }

        /** Gasta uma ficha e devolve quantas sobraram; -1 se não havia ficha. */
        synchronized long tryConsume(long now, int capacity, long refillNanos) {
            double refilled = (double) (now - lastRefill) * capacity / refillNanos;
            tokens = Math.min(capacity, tokens + refilled);
            lastRefill = now;
            if (tokens < 1) {
                return -1;
            }
            tokens -= 1;
            return (long) tokens;
        }
    }
}
