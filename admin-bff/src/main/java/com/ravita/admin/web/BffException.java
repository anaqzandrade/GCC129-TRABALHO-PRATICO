package com.ravita.admin.web;

import org.springframework.http.HttpStatus;

/** Erro do próprio BFF, já com o status e o código do contrato. */
public class BffException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BffException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BffException notFound() {
        return new BffException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Recurso não encontrado.");
    }

    public static BffException invalid(String message) {
        return new BffException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
