package com.ravita.portal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regras do próprio BFF, com todos os serviços apontando para uma porta onde nada responde. */
@SpringBootTest(properties = {
        "ravita.services.offer-url=http://localhost:1",
        "ravita.services.organization-url=http://localhost:1",
        "ravita.services.matching-url=http://localhost:1",
        "ravita.services.redistribution-url=http://localhost:1",
        "ravita.services.logistics-url=http://localhost:1",
        "ravita.services.assistant-url=http://localhost:1"
})
@AutoConfigureMockMvc
class PortalBffWebTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void institutionCannotCreateOffers() throws Exception {
        mvc.perform(post("/portal/v1/offers")
                        .header("X-Roles", "INSTITUTION").header("X-Org-Id", "org-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"Pães\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void redistributionNeedsAnOffer() throws Exception {
        mvc.perform(post("/portal/v1/redistributions")
                        .header("X-Roles", "INSTITUTION").header("X-Org-Id", "org-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void assistantNeedsAQuestion() throws Exception {
        mvc.perform(post("/portal/v1/assistant/questions")
                        .header("X-Roles", "DONOR").header("X-Org-Id", "org-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void assistantDownBecomes503() throws Exception {
        mvc.perform(post("/portal/v1/assistant/questions")
                        .header("X-Roles", "INSTITUTION").header("X-Org-Id", "org-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Posso doar iogurte?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void serviceDownBecomes503AndNeverAnEmptyList() throws Exception {
        mvc.perform(get("/portal/v1/offers")
                        .header("X-Roles", "DONOR").header("X-Org-Id", "org-1")
                        .header("X-Correlation-ID", "corr-bff-1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("X-Correlation-ID", "corr-bff-1"))
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.correlationId").value("corr-bff-1"));
    }
}
