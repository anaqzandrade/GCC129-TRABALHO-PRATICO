package com.ravita.portal.api;

import com.fasterxml.jackson.databind.JsonNode;
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

/** Demandas da instituição e os matches calculados pelo demand-matching. */
@RestController
@RequestMapping("/portal/v1/demands")
public class DemandsController {

    private static final String MATCHING = "demand-matching-service";

    private final ServiceClients services;
    private final PortalViews views;

    public DemandsController(ServiceClients services, PortalViews views) {
        this.services = services;
        this.views = views;
    }

    /** A instituição registra o que precisa. O institutionId vem do token. */
    @PostMapping
    public ResponseEntity<JsonNode> create(@RequestBody ObjectNode body, HttpServletRequest request) {
        CurrentUser user = CurrentUser.from(request);
        user.require("INSTITUTION");
        body.put("institutionId", user.requireOrg());
        JsonNode created = services.call(MATCHING, () -> services.matching().post()
                .uri("/api/v1/demands")
                .body(body)
                .retrieve()
                .body(JsonNode.class));
        return ResponseEntity.created(URI.create("/portal/v1/demands/" + Json.text(created, "id")))
                .body(views.demand(created));
    }

    /** Demandas da instituição autenticada (o administrador vê todas). */
    @GetMapping
    public JsonNode list(HttpServletRequest request,
                         @RequestParam(required = false) Integer page,
                         @RequestParam(required = false) Integer size) {
        CurrentUser user = CurrentUser.from(request);
        user.require("INSTITUTION", "ADMIN");
        String institutionId = user.isAdmin() ? null : user.requireOrg();
        JsonNode result = services.call(MATCHING, () -> services.matching().get()
                .uri(uri -> uri.path("/api/v1/demands")
                        .queryParamIfPresent("institutionId", Optional.ofNullable(institutionId))
                        .queryParamIfPresent("page", Optional.ofNullable(page))
                        .queryParamIfPresent("size", Optional.ofNullable(size))
                        .build())
                .retrieve()
                .body(JsonNode.class));
        return Json.page(views.mapper(), result, views::demand);
    }

    @GetMapping("/{id}")
    public JsonNode get(@PathVariable String id, HttpServletRequest request) {
        return views.demand(ownDemand(id, CurrentUser.from(request)));
    }

    @GetMapping("/{id}/matches")
    public JsonNode matches(@PathVariable String id, HttpServletRequest request) {
        ownDemand(id, CurrentUser.from(request));
        JsonNode matches = services.call(MATCHING, () -> services.matching().get()
                .uri("/api/v1/demands/{id}/matches", id)
                .retrieve()
                .body(JsonNode.class));
        return views.matches(matches);
    }

    /** Demanda de outra instituição responde 404, para não revelar que ela existe. */
    private JsonNode ownDemand(String id, CurrentUser user) {
        user.require("INSTITUTION", "ADMIN");
        JsonNode demand = services.findOrNull(MATCHING, () -> services.matching().get()
                .uri("/api/v1/demands/{id}", id)
                .retrieve()
                .body(JsonNode.class));
        if (demand == null || (!user.isAdmin() && !user.requireOrg().equals(Json.text(demand, "institutionId")))) {
            throw BffException.notFound();
        }
        return demand;
    }
}
