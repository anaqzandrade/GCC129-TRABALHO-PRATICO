package com.ravita.admin.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Erros sempre no formato do contrato: {code, message, correlationId}.
 * O erro de um serviço volta com o mesmo status e código (doc 02: o BFF preserva os códigos do domínio);
 * se o serviço respondeu em outro formato, o BFF converte para o do contrato.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ObjectMapper mapper;

    public GlobalExceptionHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @ExceptionHandler(BffException.class)
    public ResponseEntity<JsonNode> handleBff(BffException error) {
        return respond(error.getStatus().value(), body(error.getCode(), error.getMessage()));
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<JsonNode> handleDownstream(RestClientResponseException error) {
        JsonNode received = parse(error.getResponseBodyAsString());
        if (received instanceof ObjectNode object && object.hasNonNull("code")) {
            if (!object.hasNonNull("correlationId") && CorrelationIdFilter.current() != null) {
                object.put("correlationId", CorrelationIdFilter.current());
            }
            return respond(error.getStatusCode().value(), object);
        }
        HttpStatus status = HttpStatus.resolve(error.getStatusCode().value());
        String message = text(received, "message");
        if (message == null) {
            message = text(received, "error");
        }
        if (message == null) {
            message = status != null ? status.getReasonPhrase() : "Erro no serviço chamado.";
        }
        return respond(error.getStatusCode().value(), body(status != null ? status.name() : "ERROR", message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<JsonNode> handleBadRequest(Exception error) {
        return respond(400, body("INVALID_REQUEST", "Requisição inválida: JSON malformado ou parâmetro em formato errado."));
    }

    private ObjectNode body(String code, String message) {
        ObjectNode body = mapper.createObjectNode();
        body.put("code", code);
        body.put("message", message);
        body.put("correlationId", CorrelationIdFilter.current());
        return body;
    }

    private static ResponseEntity<JsonNode> respond(int status, JsonNode body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private JsonNode parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(text);
        } catch (Exception notJson) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field) || node.get(field).asText().isBlank()) {
            return null;
        }
        return node.get(field).asText();
    }
}
