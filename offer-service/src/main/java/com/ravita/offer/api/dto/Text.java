package com.ravita.offer.api.dto;

/** Contrato: texto é validado depois de remover espaços nas extremidades. */
final class Text {

    private Text() {
    }

    static String trim(String value) {
        return value == null ? null : value.strip();
    }

    /** Para campos opcionais: "   " vira ausente. */
    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
