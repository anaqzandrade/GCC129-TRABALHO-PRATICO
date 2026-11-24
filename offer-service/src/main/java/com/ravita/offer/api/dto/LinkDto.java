package com.ravita.offer.api.dto;

/** Relação HATEOAS do contrato: {href, method}. Links não executam comandos. */
public record LinkDto(String href, String method) {

    public static LinkDto get(String href) {
        return new LinkDto(href, "GET");
    }

    public static LinkDto post(String href) {
        return new LinkDto(href, "POST");
    }
}
