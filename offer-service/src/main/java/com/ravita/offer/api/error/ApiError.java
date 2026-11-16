package com.ravita.offer.api.error;

import java.util.List;

/** Formato de erro do contrato: {code, message, correlationId, details?}. */
public record ApiError(String code, String message, String correlationId, List<FieldIssue> details) {

    public record FieldIssue(String field, String reason) {
    }
}
