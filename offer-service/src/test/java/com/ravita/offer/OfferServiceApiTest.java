package com.ravita.offer;

import com.jayway.jsonpath.JsonPath;
import com.ravita.offer.api.error.ApiException;
import com.ravita.offer.client.OrganizationClient;
import com.ravita.offer.client.OrganizationView;
import com.ravita.offer.repository.OfferRepository;
import com.ravita.offer.repository.ReservationRepository;
import com.ravita.offer.service.OfferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de ponta a ponta do serviço (HTTP → serviço → banco H2), com o organization-service simulado.
 * Horário fixo: 20/10/2026 12:00 em Brasília; a janela das ofertas vai das 13:00 às 17:00.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OfferServiceApiTest {

    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
    private static final Instant AFTER_WINDOW = Instant.parse("2026-10-20T20:01:00Z");
    private static final String DONOR_ID = "org-donor";

    private static final String OFFER_JSON = """
            {
              "donorId": "org-donor",
              "foodCategory": "VEGETABLES",
              "description": "  Hortaliças variadas  ",
              "quantity": 30,
              "unit": "KG",
              "availableFrom": "2026-10-20T13:00:00-03:00",
              "availableUntil": "2026-10-20T17:00:00-03:00"
            }
            """;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private OfferRepository offerRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private OfferService offerService;

    @MockitoBean
    private OrganizationClient organizationClient;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        reservationRepository.deleteAll();
        offerRepository.deleteAll();
        OrganizationView donor = new OrganizationView(DONOR_ID, "DONOR", true,
                new OrganizationView.OrganizationAddress("Rua Central", "100", null, "Centro",
                        "Lavras", "MG", "37200000"));
        when(organizationClient.findById(DONOR_ID)).thenReturn(Optional.of(donor));
    }

    // ---- CreateOffer ----

    @Test
    void createsOfferCopyingDonorAddress() throws Exception {
        mvc.perform(post("/api/v1/offers")
                        .header("X-Correlation-ID", "corr-test-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OFFER_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/offers/offer-")))
                .andExpect(header().string("ETag", "\"v0\""))
                .andExpect(header().string("X-Correlation-ID", "corr-test-1"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.description").value("Hortaliças variadas"))
                .andExpect(jsonPath("$.quantity").value(30))
                .andExpect(jsonPath("$.availableFrom").value("2026-10-20T13:00:00-03:00"))
                .andExpect(jsonPath("$.pickupAddress.city").value("Lavras"))
                .andExpect(jsonPath("$.pickupAddress.complement").doesNotExist())
                .andExpect(jsonPath("$._links.self.method").value("GET"))
                .andExpect(jsonPath("$._links.reserve.method").value("POST"));
    }

    @Test
    void rejectsUnknownDonor() throws Exception {
        when(organizationClient.findById(anyString())).thenReturn(Optional.empty());

        mvc.perform(post("/api/v1/offers").contentType(MediaType.APPLICATION_JSON).content(OFFER_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_DONOR"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsInstitutionAsDonor() throws Exception {
        when(organizationClient.findById(DONOR_ID)).thenReturn(Optional.of(
                new OrganizationView(DONOR_ID, "INSTITUTION", true, null)));

        mvc.perform(post("/api/v1/offers").contentType(MediaType.APPLICATION_JSON).content(OFFER_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_DONOR"));
    }

    @Test
    void returns503WhenOrganizationServiceIsDown() throws Exception {
        when(organizationClient.findById(DONOR_ID)).thenThrow(new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", "indisponível"));

        mvc.perform(post("/api/v1/offers").contentType(MediaType.APPLICATION_JSON).content(OFFER_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void rejectsUnknownField() throws Exception {
        String body = OFFER_JSON.replace("\"unit\": \"KG\",", "\"unit\": \"KG\", \"price\": 10,");

        mvc.perform(post("/api/v1/offers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details[0].field").value("price"));
    }

    @Test
    void rejectsInvalidAddressField() throws Exception {
        String body = OFFER_JSON.replace("\"unit\": \"KG\",", """
                "unit": "KG",
                "pickupAddress": {"street": "Rua A", "number": "1", "district": "Centro",
                                  "city": "Lavras", "state": "mg", "postalCode": "37200000"},
                """);

        mvc.perform(post("/api/v1/offers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("pickupAddress.state"));
    }

    @Test
    void rejectsFractionalQuantityForUnit() throws Exception {
        String body = OFFER_JSON.replace("\"quantity\": 30", "\"quantity\": 2.5")
                .replace("\"unit\": \"KG\"", "\"unit\": \"UNIT\"");

        mvc.perform(post("/api/v1/offers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
    }

    @Test
    void rejectsWindowThatEndsBeforeItStarts() throws Exception {
        String body = OFFER_JSON.replace("2026-10-20T17:00:00-03:00", "2026-10-20T12:30:00-03:00");

        mvc.perform(post("/api/v1/offers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_WINDOW"));
    }

    // ---- QueryOffers ----

    @Test
    void filtersByCityIgnoringAccentsAndUsesContractPage() throws Exception {
        createOffer(OFFER_JSON);
        createOffer(OFFER_JSON.replace("\"unit\": \"KG\",", """
                "unit": "KG",
                "pickupAddress": {"street": "Rua B", "number": "2", "district": "Centro",
                                  "city": "São João del-Rei", "state": "MG", "postalCode": "36300000"},
                """));

        mvc.perform(get("/api/v1/offers").param("city", "sao joao DEL-REI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items[0].city").value("São João del-Rei"))
                .andExpect(jsonPath("$.items[0].pickupAddress").doesNotExist());

        mvc.perform(get("/api/v1/offers").param("status", "AVAILABLE").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void rejectsUnknownFilterAndInvalidSize() throws Exception {
        mvc.perform(get("/api/v1/offers").param("foo", "bar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("foo"));

        mvc.perform(get("/api/v1/offers").param("size", "101"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/offers").param("status", "WHATEVER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsContractErrorForUnknownOffer() throws Exception {
        mvc.perform(get("/api/v1/offers/offer-nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void statisticsIncludeEveryStatus() throws Exception {
        String first = createOffer(OFFER_JSON);
        createOffer(OFFER_JSON);
        reserve(first, "red-1");

        mvc.perform(get("/api/v1/offers/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.counts.AVAILABLE").value(1))
                .andExpect(jsonPath("$.counts.RESERVED").value(1))
                .andExpect(jsonPath("$.counts.EXPIRED").value(0));
    }

    // ---- SagaReservation ----

    @Test
    void reserveIsIdempotentForTheSameRedistribution() throws Exception {
        String offerId = createOffer(OFFER_JSON);

        String firstId = reserve(offerId, "red-1");
        String replayId = reserve(offerId, "red-1");

        assertThat(replayId).isEqualTo(firstId);
        mvc.perform(get("/api/v1/offers/{id}", offerId))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$._links.reserve").doesNotExist());
    }

    @Test
    void secondRedistributionGetsConflict() throws Exception {
        String offerId = createOffer(OFFER_JSON);
        reserve(offerId, "red-1");

        mvc.perform(post("/api/v1/offers/{id}/reservations", offerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"redistributionId\": \"red-2\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_NOT_AVAILABLE"));
    }

    @Test
    void releaseIsTheCompensationAndIsIdempotent() throws Exception {
        String offerId = createOffer(OFFER_JSON);
        String reservationId = reserve(offerId, "red-1");

        mvc.perform(delete("/api/v1/offers/{id}/reservations/{rid}", offerId, reservationId))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/offers/{id}/reservations/{rid}", offerId, reservationId))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/offers/{id}", offerId))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        mvc.perform(get("/api/v1/offers/{id}/reservations/{rid}", offerId, reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.releasedAt").exists());

        // depois de liberada, outra redistribuição pode reservar
        reserve(offerId, "red-2");
    }

    @Test
    void releasingAfterTheWindowExpiresTheOffer() throws Exception {
        String offerId = createOffer(OFFER_JSON);
        String reservationId = reserve(offerId, "red-1");
        clock.set(AFTER_WINDOW);

        mvc.perform(delete("/api/v1/offers/{id}/reservations/{rid}", offerId, reservationId))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/offers/{id}", offerId))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    void expiredOfferCannotBeReserved() throws Exception {
        String offerId = createOffer(OFFER_JSON);
        clock.set(AFTER_WINDOW);

        mvc.perform(post("/api/v1/offers/{id}/reservations", offerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"redistributionId\": \"red-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_NOT_AVAILABLE"));

        assertThat(offerService.expireOverdue()).isEqualTo(1);
        mvc.perform(get("/api/v1/offers/{id}", offerId))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    void unknownReservationReturns404() throws Exception {
        String offerId = createOffer(OFFER_JSON);

        mvc.perform(delete("/api/v1/offers/{id}/reservations/{rid}", offerId, "res-nao-existe"))
                .andExpect(status().isNotFound());
    }

    private String createOffer(String json) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/offers").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String reserve(String offerId, String redistributionId) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/offers/{id}/reservations", offerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"redistributionId\": \"" + redistributionId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/offers/" + offerId + "/reservations/res-")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
