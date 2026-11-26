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

/** Ofertas no Portal: busca, detalhe, ofertas do doador e cadastro. */
@RestController
@RequestMapping("/portal/v1")
public class OffersController {

    private static final String OFFER = "offer-service";

    private final ServiceClients services;
    private final PortalViews views;

    public OffersController(ServiceClients services, PortalViews views) {
        this.services = services;
        this.views = views;
    }

    /** Ofertas disponíveis, lidas do modelo de leitura do CQRS (GET /api/v1/offers/available). */
    @GetMapping("/offers")
    public JsonNode available(HttpServletRequest request,
                              @RequestParam(required = false) String category,
                              @RequestParam(required = false) String city,
                              @RequestParam(required = false) Integer page,
                              @RequestParam(required = false) Integer size) {
        CurrentUser user = CurrentUser.from(request);
        JsonNode result = services.call(OFFER, () -> services.offer().get()
                .uri(uri -> uri.path("/api/v1/offers/available")
                        .queryParamIfPresent("category", Optional.ofNullable(category))
                        .queryParamIfPresent("city", Optional.ofNullable(city))
                        .queryParamIfPresent("page", Optional.ofNullable(page))
                        .queryParamIfPresent("size", Optional.ofNullable(size))
                        .build())
                .retrieve()
                .body(JsonNode.class));
        return Json.page(views.mapper(), result, item -> views.offer(item, user));
    }

    /** Detalhe, lido do modelo de escrita (fonte autoritativa do estado). */
    @GetMapping("/offers/{id}")
    public JsonNode get(@PathVariable String id, HttpServletRequest request) {
        CurrentUser user = CurrentUser.from(request);
        JsonNode offer = services.findOrNull(OFFER, () -> services.offer().get()
                .uri("/api/v1/offers/{id}", id)
                .retrieve()
                .body(JsonNode.class));
        if (offer == null) {
            throw BffException.notFound();
        }
        return views.offer(offer, user);
    }

    /** Ofertas do doador autenticado: o filtro donorId vem do token, não do cliente. */
    @GetMapping("/my-offers")
    public JsonNode myOffers(HttpServletRequest request,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) Integer page,
                             @RequestParam(required = false) Integer size) {
        CurrentUser user = CurrentUser.from(request);
        user.require("DONOR");
        String donorId = user.requireOrg();
        JsonNode result = services.call(OFFER, () -> services.offer().get()
                .uri(uri -> uri.path("/api/v1/offers")
                        .queryParam("donorId", donorId)
                        .queryParamIfPresent("status", Optional.ofNullable(status))
                        .queryParamIfPresent("page", Optional.ofNullable(page))
                        .queryParamIfPresent("size", Optional.ofNullable(size))
                        .build())
                .retrieve()
                .body(JsonNode.class));
        return Json.page(views.mapper(), result, item -> views.offer(item, user));
    }

    /** Cadastro de oferta pelo doador. O donorId é sempre o do token, mesmo que o corpo traga outro. */
    @PostMapping("/offers")
    public ResponseEntity<JsonNode> create(@RequestBody ObjectNode body, HttpServletRequest request) {
        CurrentUser user = CurrentUser.from(request);
        user.require("DONOR");
        body.put("donorId", user.requireOrg());
        JsonNode created = services.call(OFFER, () -> services.offer().post()
                .uri("/api/v1/offers")
                .body(body)
                .retrieve()
                .body(JsonNode.class));
        ObjectNode view = views.offer(created, user);
        return ResponseEntity.created(URI.create("/portal/v1/offers/" + Json.text(created, "id"))).body(view);
    }
}
