package com.ravita.portal.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ravita.portal.config.ServiceClients;
import com.ravita.portal.view.Json;
import com.ravita.portal.view.PortalViews;
import com.ravita.portal.web.BffException;
import com.ravita.portal.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;

/**
 * Redistribuições no Portal. Iniciar uma redistribuição dispara a SAGA no redistribution-service;
 * a consulta agrega redistribution, offer e logistics numa resposta só.
 */
@RestController
@RequestMapping("/portal/v1/redistributions")
public class RedistributionsController {

    private static final String REDISTRIBUTION = "redistribution-service";
    private static final String OFFER = "offer-service";
    private static final String ORGANIZATION = "organization-service";
    private static final String LOGISTICS = "logistics-service";

    private final ServiceClients services;
    private final PortalViews views;
    private final ObjectMapper mapper;

    public RedistributionsController(ServiceClients services, PortalViews views, ObjectMapper mapper) {
        this.services = services;
        this.views = views;
        this.mapper = mapper;
    }

    /**
     * A instituição escolhe a oferta; o BFF completa o resto. O institutionId vem do token, o endereço
     * de coleta vem da oferta e o de entrega vem do cadastro da instituição, se o corpo não trouxer.
     */
    @PostMapping
    public ResponseEntity<JsonNode> start(@RequestBody JsonNode body, HttpServletRequest request) {
        CurrentUser user = CurrentUser.from(request);
        user.require("INSTITUTION");
        String institutionId = user.requireOrg();
        String offerId = Json.text(body, "offerId");
        if (offerId == null) {
            throw BffException.invalid("offerId é obrigatório.");
        }
        JsonNode offer = services.findOrNull(OFFER, () -> services.offer().get()
                .uri("/api/v1/offers/{id}", offerId)
                .retrieve()
                .body(JsonNode.class));
        if (offer == null) {
            throw BffException.notFound();
        }
        String pickup = Optional.ofNullable(Json.text(body, "pickupAddress"))
                .orElseGet(() -> Json.formatAddress(offer.path("pickupAddress")));
        String delivery = Optional.ofNullable(Json.text(body, "deliveryAddress"))
                .orElseGet(() -> Json.formatAddress(institutionAddress(institutionId)));

        ObjectNode command = mapper.createObjectNode()
                .put("offerId", offerId)
                .put("institutionId", institutionId)
                .put("pickupAddress", pickup)
                .put("deliveryAddress", delivery);
        JsonNode redistribution = services.call(REDISTRIBUTION, () -> services.redistribution().post()
                .uri("/api/v1/redistributions")
                .body(command)
                .retrieve()
                .body(JsonNode.class));
        String id = Json.text(redistribution, "id");
        return ResponseEntity.created(URI.create("/portal/v1/redistributions/" + id))
                .body(views.redistribution(redistribution, offer, logisticsOf(redistribution), user));
    }

    /** Redistribuições da instituição autenticada (o administrador vê todas). */
    @GetMapping
    public JsonNode list(HttpServletRequest request,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) Integer page,
                         @RequestParam(required = false) Integer size) {
        CurrentUser user = CurrentUser.from(request);
        user.require("INSTITUTION", "ADMIN");
        String institutionId = user.isAdmin() ? null : user.requireOrg();
        JsonNode result = services.call(REDISTRIBUTION, () -> services.redistribution().get()
                .uri(uri -> uri.path("/api/v1/redistributions")
                        .queryParamIfPresent("institutionId", Optional.ofNullable(institutionId))
                        .queryParamIfPresent("status", Optional.ofNullable(status))
                        .queryParamIfPresent("page", Optional.ofNullable(page))
                        .queryParamIfPresent("size", Optional.ofNullable(size))
                        .build())
                .retrieve()
                .body(JsonNode.class));
        return Json.page(mapper, result, views::redistributionSummary);
    }

    /** Agrega os três serviços. Só os participantes (instituição ou doador da oferta) e o admin veem. */
    @GetMapping("/{id}")
    public JsonNode get(@PathVariable String id, HttpServletRequest request) {
        CurrentUser user = CurrentUser.from(request);
        JsonNode redistribution = findRedistribution(id);
        JsonNode offer = offerOf(redistribution);
        requireParticipant(user, redistribution, offer);
        return views.redistribution(redistribution, offer, logisticsOf(redistribution), user);
    }

    /** Cancelamento pela instituição; a compensação (liberar a reserva) é da SAGA, não do BFF. */
    @PostMapping("/{id}/cancellation")
    public JsonNode cancel(@PathVariable String id, HttpServletRequest request) {
        CurrentUser user = CurrentUser.from(request);
        user.require("INSTITUTION");
        JsonNode redistribution = findRedistribution(id);
        if (!user.requireOrg().equals(Json.text(redistribution, "institutionId"))) {
            throw BffException.notFound();
        }
        JsonNode cancelled = services.call(REDISTRIBUTION, () -> services.redistribution().post()
                .uri("/api/v1/redistributions/{id}/cancel", id)
                .retrieve()
                .body(JsonNode.class));
        return views.redistribution(cancelled, offerOf(cancelled), logisticsOf(cancelled), user);
    }

    private JsonNode findRedistribution(String id) {
        JsonNode redistribution = services.findOrNull(REDISTRIBUTION, () -> services.redistribution().get()
                .uri("/api/v1/redistributions/{id}", id)
                .retrieve()
                .body(JsonNode.class));
        if (redistribution == null) {
            throw BffException.notFound();
        }
        return redistribution;
    }

    private JsonNode offerOf(JsonNode redistribution) {
        String offerId = Json.text(redistribution, "offerId");
        if (offerId == null) {
            return null;
        }
        return services.findOrNull(OFFER, () -> services.offer().get()
                .uri("/api/v1/offers/{id}", offerId)
                .retrieve()
                .body(JsonNode.class));
    }

    private JsonNode logisticsOf(JsonNode redistribution) {
        String logisticsId = Json.text(redistribution, "logisticsId");
        if (logisticsId == null) {
            return null;
        }
        return services.findOrNull(LOGISTICS, () -> services.logistics().get()
                .uri("/api/v1/logistics/{id}", logisticsId)
                .retrieve()
                .body(JsonNode.class));
    }

    private JsonNode institutionAddress(String institutionId) {
        JsonNode institution = services.findOrNull(ORGANIZATION, () -> services.organization().get()
                .uri("/api/v1/organizations/{id}", institutionId)
                .retrieve()
                .body(JsonNode.class));
        return institution == null ? null : institution.path("address");
    }

    /** Quem não participa recebe 404, para não revelar que a redistribuição existe. */
    private static void requireParticipant(CurrentUser user, JsonNode redistribution, JsonNode offer) {
        if (user.isAdmin()) {
            return;
        }
        String orgId = user.orgId();
        boolean institution = user.has("INSTITUTION") && orgId != null
                && orgId.equals(Json.text(redistribution, "institutionId"));
        boolean donor = user.has("DONOR") && orgId != null && offer != null
                && orgId.equals(Json.text(offer, "donorId"));
        if (!institution && !donor) {
            throw BffException.notFound();
        }
    }
}
