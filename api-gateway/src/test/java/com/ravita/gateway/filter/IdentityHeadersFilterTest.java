package com.ravita.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityHeadersFilterTest {

    private final IdentityHeadersFilter filter = new IdentityHeadersFilter();

    @Test
    void replacesIdentityHeadersSentByTheClientWithTheTokenClaims() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("donor:org-1")
                .claim("orgId", "org-1")
                .claim("roles", List.of("DONOR"))
                .build();
        MockServerHttpRequest request = MockServerHttpRequest.get("/portal/v1/offers")
                .header("X-Org-Id", "org-de-outra-pessoa")
                .header("X-Roles", "ADMIN")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request).mutate()
                .principal(Mono.<Principal>just(new JwtAuthenticationToken(jwt)))
                .build();

        HttpHeaders forwarded = forwardedHeaders(exchange);

        assertThat(forwarded.getFirst("X-User-Id")).isEqualTo("donor:org-1");
        assertThat(forwarded.getFirst("X-Org-Id")).isEqualTo("org-1");
        assertThat(forwarded.getFirst("X-Roles")).isEqualTo("DONOR");
    }

    @Test
    void requestWithoutTokenLosesTheIdentityHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/portal/v1/offers")
                .header("X-Org-Id", "org-forjada")
                .header("X-Roles", "ADMIN")
                .build();

        HttpHeaders forwarded = forwardedHeaders(MockServerWebExchange.from(request));

        assertThat(forwarded.containsKey("X-Org-Id")).isFalse();
        assertThat(forwarded.containsKey("X-Roles")).isFalse();
    }

    private HttpHeaders forwardedHeaders(ServerWebExchange exchange) {
        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        filter.filter(exchange, next -> {
            forwarded.set(next.getRequest());
            return Mono.empty();
        }).block();
        return forwarded.get().getHeaders();
    }
}
