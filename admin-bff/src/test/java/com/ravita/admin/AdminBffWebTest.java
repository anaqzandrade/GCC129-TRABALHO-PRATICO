package com.ravita.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regras do próprio BFF, com todos os serviços apontando para uma porta onde nada responde. */
@SpringBootTest(properties = {
        "ravita.services.offer-url=http://localhost:1",
        "ravita.services.organization-url=http://localhost:1",
        "ravita.services.redistribution-url=http://localhost:1",
        "ravita.services.logistics-url=http://localhost:1"
})
@AutoConfigureMockMvc
class AdminBffWebTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void onlyAdminsUseThePanel() throws Exception {
        mvc.perform(get("/admin/v1/dashboard").header("X-Roles", "DONOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void serviceDownBecomes503() throws Exception {
        mvc.perform(get("/admin/v1/dashboard").header("X-Roles", "ADMIN"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }
}
