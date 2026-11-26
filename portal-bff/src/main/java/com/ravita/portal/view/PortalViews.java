package com.ravita.portal.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ravita.portal.web.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.ravita.portal.view.Json.copy;
import static com.ravita.portal.view.Json.text;

/**
 * Representações do Portal (doadores e instituições). A diferença para o Admin está aqui:
 * o Portal mostra o que a pessoa precisa para a tarefa dela, em linguagem simples, e esconde o que é
 * interno (reserva, operação logística, estado da SAGA, correlation id, eventos de diagnóstico).
 * Os links HATEOAS apontam só para rotas públicas /portal/v1 (doc 02, seção 14 e regras de links).
 */
@Component
public class PortalViews {

    private static final String BASE = "/portal/v1";
    private static final Set<String> CANCELLABLE = Set.of("CREATED", "OFFER_RESERVED", "LOGISTICS_PENDING");
    /* campos de diagnóstico do modelo de leitura que o Portal não mostra */
    private static final Set<String> INTERNAL_OFFER_FIELDS = Set.of("lastEvent", "eventOccurredAt", "projectedAt");

    private final ObjectMapper mapper;

    public PortalViews(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    /**
     * Oferta para o Portal: tira os campos internos e troca os links internos (donor, reserve) pelos
     * públicos. A instituição vê start-redistribution enquanto a oferta estiver disponível.
     */
    public ObjectNode offer(JsonNode source, CurrentUser user) {
        ObjectNode offer = source.deepCopy();
        INTERNAL_OFFER_FIELDS.forEach(offer::remove);
        String id = text(source, "id");
        ObjectNode links = mapper.createObjectNode();
        links.set("self", Json.link(mapper, BASE + "/offers/" + id, "GET"));
        if (user.has("INSTITUTION") && "AVAILABLE".equals(text(source, "status"))) {
            links.set("start-redistribution", Json.link(mapper, BASE + "/redistributions", "POST"));
        }
        offer.set("_links", links);
        return offer;
    }

    public ObjectNode demand(JsonNode source) {
        ObjectNode demand = source.deepCopy();
        String id = text(source, "id");
        ObjectNode links = mapper.createObjectNode();
        links.set("self", Json.link(mapper, BASE + "/demands/" + id, "GET"));
        links.set("matches", Json.link(mapper, BASE + "/demands/" + id + "/matches", "GET"));
        demand.set("_links", links);
        return demand;
    }

    /** Matches da demanda, cada um com o link para ver a oferta no Portal. */
    public ObjectNode matches(JsonNode source) {
        ObjectNode result = source.deepCopy();
        ArrayNode matches = mapper.createArrayNode();
        source.path("matches").forEach(match -> {
            ObjectNode item = match.deepCopy();
            ObjectNode links = mapper.createObjectNode();
            links.set("offer", Json.link(mapper, BASE + "/offers/" + text(match, "offerId"), "GET"));
            item.set("_links", links);
            matches.add(item);
        });
        result.set("matches", matches);
        return result;
    }

    /**
     * Redistribuição para o Portal, agregando três serviços: redistribution (andamento), offer (o que é a
     * doação) e logistics (coleta e entrega). Formato orientado à tarefa do doc 02, seção 14.
     */
    public ObjectNode redistribution(JsonNode redistribution, JsonNode offer, JsonNode logistics, CurrentUser user) {
        String id = text(redistribution, "id");
        String status = text(redistribution, "status");
        ObjectNode view = mapper.createObjectNode();
        view.put("id", id);
        view.put("status", status);
        view.put("statusDescription", describe(status));
        if ("FAILED".equals(status)) {
            view.put("problem", problem(text(redistribution, "failureReason")));
        }

        ObjectNode offerView = view.putObject("offer");
        offerView.put("id", text(redistribution, "offerId"));
        if (offer != null) {
            copy(offerView, "description", offer, "description");
            copy(offerView, "foodCategory", offer, "foodCategory");
            copy(offerView, "quantity", offer, "quantity");
            copy(offerView, "unit", offer, "unit");
            copy(offerView, "city", offer.path("pickupAddress"), "city");
        }

        ObjectNode delivery = view.putObject("delivery");
        if (logistics != null) {
            copy(delivery, "status", logistics, "status");
            copy(delivery, "pickupAddress", logistics, "pickupAddress");
            copy(delivery, "deliveryAddress", logistics, "deliveryAddress");
            copy(delivery, "collectedAt", logistics, "collectedAt");
            copy(delivery, "deliveredAt", logistics, "deliveredAt");
        } else {
            delivery.put("status", "NOT_SCHEDULED");
        }

        copy(view, "createdAt", redistribution, "createdAt");
        copy(view, "updatedAt", redistribution, "updatedAt");

        ObjectNode links = view.putObject("_links");
        links.set("self", Json.link(mapper, BASE + "/redistributions/" + id, "GET"));
        links.set("offer", Json.link(mapper, BASE + "/offers/" + text(redistribution, "offerId"), "GET"));
        if (user.has("INSTITUTION") && CANCELLABLE.contains(status)) {
            links.set("cancel", Json.link(mapper, BASE + "/redistributions/" + id + "/cancellation", "POST"));
        }
        return view;
    }

    /** Item curto da lista de redistribuições da instituição. */
    public ObjectNode redistributionSummary(JsonNode redistribution) {
        String id = text(redistribution, "id");
        String status = text(redistribution, "status");
        ObjectNode view = mapper.createObjectNode();
        view.put("id", id);
        view.put("status", status);
        view.put("statusDescription", describe(status));
        view.put("offerId", text(redistribution, "offerId"));
        copy(view, "createdAt", redistribution, "createdAt");
        view.putObject("_links").set("self", Json.link(mapper, BASE + "/redistributions/" + id, "GET"));
        return view;
    }

    static String describe(String status) {
        if (status == null) {
            return "Situação desconhecida";
        }
        return switch (status) {
            case "CREATED" -> "Pedido registrado";
            case "OFFER_RESERVED" -> "Oferta reservada para a sua instituição";
            case "LOGISTICS_PENDING" -> "Coleta agendada";
            case "IN_TRANSIT" -> "Doação coletada e a caminho";
            case "DELIVERED" -> "Doação entregue";
            case "COMPLETED" -> "Redistribuição concluída";
            case "FAILED" -> "Não foi possível concluir a redistribuição";
            case "CANCELLED" -> "Redistribuição cancelada";
            default -> "Situação desconhecida";
        };
    }

    /** O motivo técnico da falha vira uma explicação para a instituição. */
    static String problem(String failureReason) {
        if (failureReason == null) {
            return "Não foi possível concluir a redistribuição.";
        }
        if (failureReason.contains("OFFER_NOT_AVAILABLE")) {
            return "A oferta não está mais disponível.";
        }
        if (failureReason.contains("operação logística")) {
            return "Não foi possível agendar a coleta agora; a reserva da oferta foi desfeita. Tente de novo mais tarde.";
        }
        return "Não foi possível concluir a redistribuição.";
    }
}
