package com.ravita.admin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Quem está chamando, segundo os cabeçalhos que o API Gateway preenche a partir do token validado.
 * O BFF confia neles porque só o gateway o alcança, pela rede interna bff.
 */
public record CurrentUser(String userId, String orgId, Set<String> roles) {

    public static final String USER_ID = "X-User-Id";
    public static final String ORG_ID = "X-Org-Id";
    public static final String ROLES = "X-Roles";

    public static CurrentUser from(HttpServletRequest request) {
        String roles = request.getHeader(ROLES);
        Set<String> parsed = roles == null ? Set.of() : Arrays.stream(roles.split(","))
                .map(String::strip)
                .filter(role -> !role.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        return new CurrentUser(request.getHeader(USER_ID), blankToNull(request.getHeader(ORG_ID)), parsed);
    }

    public boolean has(String role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return has("ADMIN");
    }

    /** 403 se o usuário não tiver nenhum dos papéis. */
    public void require(String... anyOf) {
        for (String role : anyOf) {
            if (has(role)) {
                return;
            }
        }
        throw new BffException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "Esta ação exige o perfil " + String.join(" ou ", anyOf) + ".");
    }

    /** A organização do token; 403 se o token não tiver uma. */
    public String requireOrg() {
        if (orgId == null) {
            throw new BffException(HttpStatus.FORBIDDEN, "FORBIDDEN", "O token não está ligado a uma organização.");
        }
        return orgId;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
