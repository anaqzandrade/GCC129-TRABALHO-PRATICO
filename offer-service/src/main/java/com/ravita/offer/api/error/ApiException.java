package com.ravita.offer.api.error;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Erro com status e código do contrato (ex.: 409 OFFER_NOT_AVAILABLE).
 * O GlobalExceptionHandler transforma em {code, message, correlationId}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<ApiError.FieldIssue> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, List<ApiError.FieldIssue> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Recurso não encontrado.");
    }

    public static ApiException invalidRequest(String field, String reason) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Requisição inválida.",
                List.of(new ApiError.FieldIssue(field, reason)));
    }

    public static ApiException businessRule(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public List<ApiError.FieldIssue> getDetails() { return details; }
}
