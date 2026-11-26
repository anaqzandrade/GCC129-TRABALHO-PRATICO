package com.ravita.portal.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ravita.portal.config.ServiceClients;
import com.ravita.portal.view.Json;
import com.ravita.portal.view.PortalViews;
import com.ravita.portal.web.BffException;
import com.ravita.portal.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assistente de doação (doc 07). O Portal repassa só a pergunta: a recuperação fica sempre ligada, e o
 * useRetrieval: false do assistant-service é só para a avaliação.
 */
@RestController
@RequestMapping("/portal/v1/assistant")
public class AssistantController {

    private static final String ASSISTANT = "assistant-service";

    private final ServiceClients services;
    private final PortalViews views;

    public AssistantController(ServiceClients services, PortalViews views) {
        this.services = services;
        this.views = views;
    }

    @PostMapping("/questions")
    public JsonNode ask(@RequestBody JsonNode body, HttpServletRequest request) {
        CurrentUser.from(request).require("DONOR", "INSTITUTION", "ADMIN");
        String question = Json.text(body, "question");
        if (question == null || question.isBlank()) {
            throw BffException.invalid("question é obrigatório.");
        }
        ObjectNode forward = views.mapper().createObjectNode().put("question", question);
        JsonNode answer = services.call(ASSISTANT, () -> services.assistant().post()
                .uri("/api/v1/assistant/questions")
                .body(forward)
                .retrieve()
                .body(JsonNode.class));
        return views.assistant(answer);
    }
}
