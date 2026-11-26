package com.ravita.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gateway de verdade, numa porta aleatória. Os BFFs apontam para uma porta onde nada responde,
 * então uma requisição autorizada termina em 503: isso também mostra que ela passou pelos filtros.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "PORTAL_BFF_URL=http://localhost:1",
        "ADMIN_BFF_URL=http://localhost:1",
        "WEB_URL=http://localhost:1",
        "ravita.rate-limit.capacity=10",
        "ravita.rate-limit.refill-period=1h"
})
class ApiGatewayTest {

    @Autowired
    private WebTestClient client;

    @Test
    void issuesATokenForAKnownRole() {
        assertThat(token("DONOR", "org-1")).contains(".");
    }

    @Test
    void rootRedirectsToThePortalPage() {
        client.get().uri("/")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals("Location", "/app/portal/");
    }

    @Test
    void pagesNeedNoTokenAndAreServedByTheWebContainer() {
        // sem token e sem 401: a requisição chega à rota do container web, que no teste não existe
        client.get().uri("/app/portal/")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void rejectsAnUnknownRole() {
        client.post().uri("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("role", "HACKER"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_ROLE");
    }

    @Test
    void requestWithoutTokenGets401WithCorrelationId() {
        client.get().uri("/portal/v1/offers")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Correlation-ID")
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.correlationId").exists();
    }

    @Test
    void keepsTheCorrelationIdSentByTheClient() {
        client.get().uri("/portal/v1/offers")
                .header("X-Correlation-ID", "corr-teste-1")
                .exchange()
                .expectHeader().valueEquals("X-Correlation-ID", "corr-teste-1");
    }

    @Test
    void donorTokenCannotReachAdminRoutes() {
        String token = token("DONOR", "org-1");
        client.get().uri("/admin/v1/dashboard")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void authorizedRequestToAnUnavailableBffGets503() {
        String token = token("DONOR", "org-1");
        client.get().uri("/portal/v1/offers")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void tooManyRequestsFromTheSameClientGet429() {
        // um "token" só deste teste: o balde dele não divide fichas com os outros testes
        String authorization = "Bearer limite-" + UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            client.get().uri("/portal/v1/offers")
                    .header("Authorization", authorization)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
        client.get().uri("/portal/v1/offers")
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().exists("Retry-After")
                .expectBody().jsonPath("$.code").isEqualTo("RATE_LIMITED");
    }

    @SuppressWarnings("unchecked")
    private String token(String role, String organizationId) {
        Map<String, Object> body = client.post().uri("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("role", role, "organizationId", organizationId))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertThat(body).isNotNull();
        return (String) body.get("accessToken");
    }
}
