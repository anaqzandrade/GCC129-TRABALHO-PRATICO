package com.ravita.offer.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Parte da resposta de GET /api/v1/organizations/{id} que o offer-service usa.
 * ignoreUnknown: o organization-service pode devolver mais campos sem quebrar este serviço
 * (necessário porque a entrada deste serviço rejeita campos desconhecidos).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrganizationView(String id, String name, String type, Boolean active, OrganizationAddress address) {

    public boolean isActiveDonor() {
        return "DONOR".equals(type) && Boolean.TRUE.equals(active);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrganizationAddress(String street, String number, String complement, String district,
                                      String city, String state, String postalCode) {
    }
}
