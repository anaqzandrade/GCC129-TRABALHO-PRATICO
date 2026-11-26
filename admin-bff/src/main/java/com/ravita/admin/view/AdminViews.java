package com.ravita.admin.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static com.ravita.admin.view.Json.copy;
import static com.ravita.admin.view.Json.text;

/**
 * Representações do painel administrativo. A diferença para o Portal está aqui: o Admin mostra
 * identificadores internos, o estado da SAGA, a compensação, o último evento e o correlation id,
 * para diagnosticar uma redistribuição de ponta a ponta (doc 02, seção 14).
 */
@Component
public class AdminViews {

    private final ObjectMapper mapper;

    public AdminViews(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    /** Diagnóstico de uma redistribuição, juntando redistribution, offer e logistics. */
    public ObjectNode redistributionDetail(JsonNode redistribution, JsonNode offer, JsonNode logistics) {
        String status = text(redistribution, "status");
        String lastEvent = text(redistribution, "lastEvent");
        ObjectNode view = mapper.createObjectNode();
        copy(view, "id", redistribution, "id");
        view.put("status", status);
        view.put("sagaState", sagaState(status));
        view.put("compensation", compensation(status, lastEvent));
        view.put("lastEvent", lastEvent);
        copy(view, "correlationId", redistribution, "correlationId");
        copy(view, "failureReason", redistribution, "failureReason");
        copy(view, "offerId", redistribution, "offerId");
        if (offer != null) {
            copy(view, "donorId", offer, "donorId");
        }
        copy(view, "institutionId", redistribution, "institutionId");
        copy(view, "reservationId", redistribution, "reservationId");
        copy(view, "logisticsId", redistribution, "logisticsId");

        ObjectNode offerView = view.putObject("offer");
        if (offer != null) {
            copy(offerView, "status", offer, "status");
            copy(offerView, "description", offer, "description");
            copy(offerView, "updatedAt", offer, "updatedAt");
        }
        if (logistics != null) {
            ObjectNode logisticsView = view.putObject("logistics");
            copy(logisticsView, "status", logistics, "status");
            copy(logisticsView, "collectedAt", logistics, "collectedAt");
            copy(logisticsView, "deliveredAt", logistics, "deliveredAt");
        } else {
            view.putNull("logistics");
        }
        copy(view, "createdAt", redistribution, "createdAt");
        copy(view, "updatedAt", redistribution, "updatedAt");
        view.putObject("_links").set("self",
                Json.link(mapper, "/admin/v1/redistributions/" + text(redistribution, "id"), "GET"));
        return view;
    }

    /** Linha da lista de redistribuições do painel. */
    public ObjectNode redistributionSummary(JsonNode redistribution) {
        String status = text(redistribution, "status");
        ObjectNode view = mapper.createObjectNode();
        copy(view, "id", redistribution, "id");
        view.put("status", status);
        view.put("sagaState", sagaState(status));
        copy(view, "lastEvent", redistribution, "lastEvent");
        copy(view, "offerId", redistribution, "offerId");
        copy(view, "institutionId", redistribution, "institutionId");
        copy(view, "failureReason", redistribution, "failureReason");
        copy(view, "correlationId", redistribution, "correlationId");
        copy(view, "createdAt", redistribution, "createdAt");
        view.putObject("_links").set("self",
                Json.link(mapper, "/admin/v1/redistributions/" + text(redistribution, "id"), "GET"));
        return view;
    }

    /** Indicadores do painel, com as contagens de três serviços. */
    public ObjectNode dashboard(long donors, long institutions, JsonNode offerStatistics,
                                JsonNode redistributionStatistics, ZoneId zone) {
        ObjectNode view = mapper.createObjectNode();
        ObjectNode organizations = view.putObject("organizations");
        organizations.put("donors", donors);
        organizations.put("institutions", institutions);

        ObjectNode offers = view.putObject("offers");
        offers.put("total", offerStatistics.path("total").asLong());
        offers.set("byStatus", offerStatistics.path("counts"));

        ObjectNode redistributions = view.putObject("redistributions");
        redistributions.put("total", redistributionStatistics.path("total").asLong());
        redistributions.set("byStatus", redistributionStatistics.path("byStatus"));

        view.put("generatedAt", OffsetDateTime.now(zone).toString());
        return view;
    }

    /** Estado da SAGA nos termos do doc 04, seção 4. */
    static String sagaState(String status) {
        if (status == null) {
            return "UNKNOWN";
        }
        return switch (status) {
            case "CREATED" -> "CREATED";
            case "OFFER_RESERVED" -> "OFFER_RESERVED";
            case "LOGISTICS_PENDING" -> "LOGISTICS_SCHEDULED";
            case "IN_TRANSIT" -> "IN_TRANSIT";
            case "DELIVERED" -> "DELIVERED";
            case "COMPLETED" -> "COMPLETED";
            case "CANCELLED" -> "CANCELLED";
            case "FAILED" -> "FAILED";
            default -> "UNKNOWN";
        };
    }

    /** Situação da compensação: DONE (reserva liberada), PENDING (falhou), NOT_NEEDED ou NONE. */
    static String compensation(String status, String lastEvent) {
        if ("ReservationReleased".equals(lastEvent)) {
            return "DONE";
        }
        if ("CompensationPending".equals(lastEvent)) {
            return "PENDING";
        }
        if ("FAILED".equals(status)) {
            return "NOT_NEEDED";
        }
        return "NONE";
    }
}
