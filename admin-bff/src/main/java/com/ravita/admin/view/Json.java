package com.ravita.admin.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.function.Function;

/** Pequenas ajudas para montar as respostas a partir do JSON dos serviços. */
public final class Json {

    private Json() {
    }

    /** Texto de um campo, ou null se faltar, for null ou vazio. */
    public static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field) || node.get(field).asText().isBlank()) {
            return null;
        }
        return node.get(field).asText();
    }

    /** Copia o campo se ele existir e não for null. */
    public static void copy(ObjectNode target, String name, JsonNode source, String field) {
        if (source != null && source.hasNonNull(field)) {
            target.set(name, source.get(field));
        }
    }

    public static ObjectNode link(ObjectMapper mapper, String href, String method) {
        return mapper.createObjectNode().put("href", href).put("method", method);
    }

    /**
     * Página no formato do contrato, {items, page, size, total}, a partir de qualquer um dos formatos que
     * os serviços devolvem: o do contrato (offer, redistribution, logistics) ou o Page do Spring Data
     * (organization, matching), com page começando em 1.
     */
    public static ObjectNode page(ObjectMapper mapper, JsonNode source, Function<JsonNode, JsonNode> itemMapper) {
        ObjectNode page = mapper.createObjectNode();
        ArrayNode items = page.putArray("items");
        JsonNode content = source.has("items") ? source.get("items") : source.path("content");
        content.forEach(item -> items.add(itemMapper.apply(item)));
        if (source.has("items")) {
            page.put("page", source.path("page").asInt(1));
            page.put("size", source.path("size").asInt(items.size()));
            page.put("total", source.path("total").asLong(items.size()));
        } else {
            JsonNode meta = source.has("page") && source.get("page").isObject() ? source.get("page") : source;
            page.put("page", meta.path("number").asInt(0) + 1);
            page.put("size", meta.path("size").asInt(items.size()));
            page.put("total", meta.path("totalElements").asLong(items.size()));
        }
        return page;
    }

    /** "Rua Central, 100 - Centro, Lavras/MG" a partir de um endereço do organization ou do offer. */
    public static String formatAddress(JsonNode address) {
        if (address == null || address.isMissingNode() || address.isNull()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        append(text, text(address, "street"), "");
        append(text, text(address, "number"), ", ");
        append(text, text(address, "district"), " - ");
        append(text, text(address, "city"), ", ");
        append(text, text(address, "state"), "/");
        return text.isEmpty() ? null : text.toString();
    }

    private static void append(StringBuilder text, String value, String separator) {
        if (value != null) {
            text.append(text.isEmpty() ? "" : separator).append(value);
        }
    }
}
