package com.ravita.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Repassa ao BFF a identidade que o gateway validou (doc 02, seção 11).
 *
 * <p>Um identificador enviado pelo cliente não prova quem ele é: os cabeçalhos X-User-Id, X-Org-Id e
 * X-Roles que chegarem de fora são descartados e preenchidos de novo a partir do token já validado.
 * Os BFFs confiam nesses cabeçalhos porque só o gateway os alcança, pela rede interna.
 */
@Component
public class IdentityHeadersFilter implements GlobalFilter, Ordered {

    public static final String USER_ID = "X-User-Id";
    public static final String ORG_ID = "X-Org-Id";
    public static final String ROLES = "X-Roles";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(authentication -> withIdentity(exchange, authentication.getToken()))
                .defaultIfEmpty(withIdentity(exchange, null))
                .flatMap(chain::filter);
    }

    private static ServerWebExchange withIdentity(ServerWebExchange exchange, Jwt jwt) {
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            headers.remove(USER_ID);
            headers.remove(ORG_ID);
            headers.remove(ROLES);
            if (jwt == null) {
                return;
            }
            if (jwt.getSubject() != null) {
                headers.set(USER_ID, jwt.getSubject());
            }
            String orgId = jwt.getClaimAsString("orgId");
            if (orgId != null) {
                headers.set(ORG_ID, orgId);
            }
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null && !roles.isEmpty()) {
                headers.set(ROLES, String.join(",", roles));
            }
        }).build();
        return exchange.mutate().request(request).build();
    }

    /** Antes do filtro que encaminha a requisição ao BFF. */
    @Override
    public int getOrder() {
        return 0;
    }
}
