package com.ravita.offer.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Contrato: X-Correlation-ID (1–128 caracteres [A-Za-z0-9._-]) é propagado e devolvido.
 * Se vier ausente ou inválido, o serviço gera um. O valor vai para o MDC (aparece nos logs)
 * e é repassado nas chamadas ao organization-service.
 * Cada requisição (exceto /actuator, usado pelo healthcheck) gera uma linha de log
 * com método, caminho, status e duração.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String received = request.getHeader(HEADER);
        String correlationId = received != null && VALID.matcher(received).matches()
                ? received
                : "corr-" + UUID.randomUUID();
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (!request.getRequestURI().startsWith("/actuator")) {
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                log.info("{} {} {} {}ms", request.getMethod(), request.getRequestURI(),
                        response.getStatus(), elapsedMs);
            }
            MDC.remove(MDC_KEY);
        }
    }

    /** Correlation id da requisição atual (ou null fora de uma requisição). */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
