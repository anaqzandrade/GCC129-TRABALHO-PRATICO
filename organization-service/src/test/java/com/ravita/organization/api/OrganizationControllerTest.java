package com.ravita.organization.api;

import com.ravita.organization.domain.*;
import com.ravita.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrganizationController.class)
class OrganizationControllerTest {
    @Autowired MockMvc mvc;
    @MockBean OrganizationRepository repository;
    private String input(){return "{\"name\":\"Mercado Central\",\"type\":\"DONOR\",\"document\":\"12345678000199\",\"address\":{\"street\":\"Rua Central\",\"number\":\"100\",\"district\":\"Centro\",\"city\":\"Lavras\",\"state\":\"MG\",\"postalCode\":\"37200000\"}}";}
    @Test void createsOrganizationAndReturnsLocation() throws Exception { when(repository.existsByDocument("12345678000199")).thenReturn(false); when(repository.save(any(Organization.class))).thenAnswer(i->i.getArgument(0)); mvc.perform(post("/api/v1/organizations").contentType(MediaType.APPLICATION_JSON).content(input())).andExpect(status().isCreated()).andExpect(header().string("Location",org.hamcrest.Matchers.startsWith("/api/v1/organizations/org-"))); }
    @Test void rejectsDuplicateDocument() throws Exception { when(repository.existsByDocument("12345678000199")).thenReturn(true); mvc.perform(post("/api/v1/organizations").contentType(MediaType.APPLICATION_JSON).content(input())).andExpect(status().isConflict()); verify(repository,never()).save(any()); }
    @Test void returnsOrganizationById() throws Exception { Organization o=new Organization("Mercado Central",OrganizationType.DONOR,"12345678000199",new Address("Rua Central","100","Centro","Lavras","MG","37200000"),java.util.List.of()); when(repository.findById(o.getId())).thenReturn(Optional.of(o)); mvc.perform(get("/api/v1/organizations/"+o.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.type").value("DONOR")); }
}
