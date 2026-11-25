package com.ravita.gateway.security;

import com.ravita.gateway.config.GatewayProperties;
import com.ravita.gateway.filter.CorrelationIdWebFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Emissor de token de DESENVOLVIMENTO: faz o papel de um provedor de identidade (Keycloak, Auth0...)
 * para a demonstração. Não tem senha; recebe o papel e a organização e devolve um JWT assinado.
 * Em produção este endpoint não existiria: o gateway só validaria tokens emitidos pelo provedor.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Set<String> ROLES = Set.of("DONOR", "INSTITUTION", "ADMIN");

    private final JwtEncoder encoder;
    private final GatewayProperties properties;

    public AuthController(JwtEncoder encoder, GatewayProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public record TokenRequest(String role, String organizationId) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, String role,
                                String organizationId) {
    }

    @PostMapping("/token")
    public ResponseEntity<?> token(@RequestBody TokenRequest request, ServerWebExchange exchange) {
        String role = request.role() == null ? null : request.role().strip().toUpperCase();
        if (role == null || !ROLES.contains(role)) {
            return error(exchange, "INVALID_ROLE", "role deve ser DONOR, INSTITUTION ou ADMIN.");
        }
        String organizationId = request.organizationId() == null || request.organizationId().isBlank()
                ? null : request.organizationId().strip();
        if (organizationId == null && !role.equals("ADMIN")) {
            return error(exchange, "INVALID_REQUEST", "organizationId é obrigatório para DONOR e INSTITUTION.");
        }

        Instant now = Instant.now();
        Duration ttl = properties.jwt().ttl();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(organizationId == null ? "admin" : role.toLowerCase() + ":" + organizationId)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("roles", List.of(role));
        if (organizationId != null) {
            claims.claim("orgId", organizationId);
        }
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                claims.build())).getTokenValue();
        return ResponseEntity.ok(new TokenResponse(token, "Bearer", ttl.toSeconds(), role, organizationId));
    }

    private static ResponseEntity<Map<String, String>> error(ServerWebExchange exchange, String code,
                                                             String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("correlationId", CorrelationIdWebFilter.correlationId(exchange));
        return ResponseEntity.badRequest().body(body);
    }
}
