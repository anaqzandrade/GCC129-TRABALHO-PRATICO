package com.ravita.admin.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.ravita.admin.config.ServiceClients;
import com.ravita.admin.view.AdminViews;
import com.ravita.admin.view.Json;
import com.ravita.admin.web.BffException;
import com.ravita.admin.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Painel administrativo. O gateway já exige o papel ADMIN em /admin/v1; o BFF confere de novo
 * (defesa em profundidade).
 */
@RestController
@RequestMapping("/admin/v1")
public class AdminController {

    private static final String ORGANIZATION = "organization-service";
    private static final String OFFER = "offer-service";
    private static final String REDISTRIBUTION = "redistribution-service";
    private static final String LOGISTICS = "logistics-service";

    private final ServiceClients services;
    private final AdminViews views;
    private final ZoneId zone;

    public AdminController(ServiceClients services, AdminViews views,
                           @Value("${ravita.time-zone:America/Sao_Paulo}") String zone) {
        this.services = services;
        this.views = views;
        this.zone = ZoneId.of(zone);
    }

    /** Contagens de organizações, ofertas e redistribuições por estado (doc 02, seção 13). */
    @GetMapping("/dashboard")
    public JsonNode dashboard(HttpServletRequest request) {
        requireAdmin(request);
        long donors = organizationTotal("DONOR");
        long institutions = organizationTotal("INSTITUTION");
        JsonNode offers = services.call(OFFER, () -> services.offer().get()
                .uri("/api/v1/offers/statistics")
                .retrieve()
                .body(JsonNode.class));
        JsonNode redistributions = services.call(REDISTRIBUTION, () -> services.redistribution().get()
                .uri("/api/v1/redistributions/statistics")
                .retrieve()
                .body(JsonNode.class));
        return views.dashboard(donors, institutions, offers, redistributions, zone);
    }

    @GetMapping("/organizations")
    public JsonNode organizations(HttpServletRequest request,
                                  @RequestParam(required = false) String type,
                                  @RequestParam(required = false) Boolean active,
                                  @RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer size) {
        requireAdmin(request);
        JsonNode result = services.call(ORGANIZATION, () -> services.organization().get()
                .uri(uri -> uri.path("/api/v1/organizations")
                        .queryParamIfPresent("type", Optional.ofNullable(type))
                        .queryParamIfPresent("active", Optional.ofNullable(active))
                        .queryParamIfPresent("page", Optional.ofNullable(page))
                        .queryParamIfPresent("size", Optional.ofNullable(size))
                        .build())
                .retrieve()
                .body(JsonNode.class));
        return Json.page(views.mapper(), result, organization -> organization);
    }

    /** Cadastro de doadores e instituições, feito pela equipe da Ravita. */
    @PostMapping("/organizations")
    public ResponseEntity<JsonNode> createOrganization(@RequestBody JsonNode body, HttpServletRequest request) {
        requireAdmin(request);
        JsonNode created = services.call(ORGANIZATION, () -> services.organization().post()
                .uri("/api/v1/organizations")
                .body(body)
                .retrieve()
                .body(JsonNode.class));
        return ResponseEntity.created(URI.create("/admin/v1/organizations/" + Json.text(created, "id")))
                .body(created);
    }

    @GetMapping("/organizations/{id}")
    public JsonNode organization(@PathVariable String id, HttpServletRequest request) {
        requireAdmin(request);
        JsonNode organization = services.findOrNull(ORGANIZATION, () -> services.organization().get()
                .uri("/api/v1/organizations/{id}", id)
                .retrieve()
                .body(JsonNode.class));
        if (organization == null) {
            throw BffException.notFound();
        }
        return organization;
    }

    /** Lista para acompanhar a SAGA; status=FAILED mostra as falhas e se a compensação rodou. */
    @GetMapping("/redistributions")
    public JsonNode redistributions(HttpServletRequest request,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String institutionId,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size) {
        requireAdmin(request);
        JsonNode result = services.call(REDISTRIBUTION, () -> services.redistribution().get()
                .uri(uri -> uri.path("/api/v1/redistributions")
                        .queryParamIfPresent("status", Optional.ofNullable(status))
                        .queryParamIfPresent("institutionId", Optional.ofNullable(institutionId))
                        .queryParamIfPresent("page", Optional.ofNullable(page))
                        .queryParamIfPresent("size", Optional.ofNullable(size))
                        .build())
                .retrieve()
                .body(JsonNode.class));
        return Json.page(views.mapper(), result, views::redistributionSummary);
    }

    /** Diagnóstico de uma redistribuição: o mesmo recurso que o Portal mostra, com os dados internos. */
    @GetMapping("/redistributions/{id}")
    public JsonNode redistribution(@PathVariable String id, HttpServletRequest request) {
        requireAdmin(request);
        JsonNode redistribution = services.findOrNull(REDISTRIBUTION, () -> services.redistribution().get()
                .uri("/api/v1/redistributions/{id}", id)
                .retrieve()
                .body(JsonNode.class));
        if (redistribution == null) {
            throw BffException.notFound();
        }
        String offerId = Json.text(redistribution, "offerId");
        JsonNode offer = offerId == null ? null : services.findOrNull(OFFER, () -> services.offer().get()
                .uri("/api/v1/offers/{id}", offerId)
                .retrieve()
                .body(JsonNode.class));
        String logisticsId = Json.text(redistribution, "logisticsId");
        JsonNode logistics = logisticsId == null ? null : services.findOrNull(LOGISTICS, () -> services.logistics().get()
                .uri("/api/v1/logistics/{id}", logisticsId)
                .retrieve()
                .body(JsonNode.class));
        return views.redistributionDetail(redistribution, offer, logistics);
    }

    /** Operações logísticas; status=FAILED atende a tela de falhas logísticas. */
    @GetMapping("/logistics")
    public JsonNode logistics(HttpServletRequest request,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) Integer page,
                              @RequestParam(required = false) Integer size) {
        requireAdmin(request);
        JsonNode result = services.call(LOGISTICS, () -> services.logistics().get()
                .uri(uri -> uri.path("/api/v1/logistics")
                        .queryParamIfPresent("status", Optional.ofNullable(status))
                        .queryParamIfPresent("page", Optional.ofNullable(page))
                        .queryParamIfPresent("size", Optional.ofNullable(size))
                        .build())
                .retrieve()
                .body(JsonNode.class));
        return Json.page(views.mapper(), result, operation -> operation);
    }

    /**
     * Área logística do painel: registra a coleta. O logistics publica logistics.food.collected e a SAGA
     * avança a redistribuição para IN_TRANSIT; o BFF não altera nenhum estado por conta própria.
     */
    @PostMapping("/logistics/{id}/collection")
    public JsonNode collect(@PathVariable String id, HttpServletRequest request) {
        requireAdmin(request);
        return services.call(LOGISTICS, () -> services.logistics().post()
                .uri("/api/v1/logistics/{id}/collect", id)
                .retrieve()
                .body(JsonNode.class));
    }

    /** Registra a entrega: logistics.food.delivered leva a redistribuição a COMPLETED. */
    @PostMapping("/logistics/{id}/delivery")
    public JsonNode deliver(@PathVariable String id, HttpServletRequest request) {
        requireAdmin(request);
        return services.call(LOGISTICS, () -> services.logistics().post()
                .uri("/api/v1/logistics/{id}/deliver", id)
                .retrieve()
                .body(JsonNode.class));
    }

    private long organizationTotal(String type) {
        JsonNode page = services.call(ORGANIZATION, () -> services.organization().get()
                .uri("/api/v1/organizations?type={type}&page=1&size=1", type)
                .retrieve()
                .body(JsonNode.class));
        return Json.page(views.mapper(), page, item -> item).path("total").asLong();
    }

    private static void requireAdmin(HttpServletRequest request) {
        CurrentUser.from(request).require("ADMIN");
    }
}
