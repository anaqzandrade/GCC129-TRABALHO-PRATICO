package com.ravita.portal.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ravita.portal.web.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** O que o Portal mostra e o que ele esconde, sem HTTP. */
class PortalViewsTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PortalViews views = new PortalViews(mapper);
    private final CurrentUser institution = new CurrentUser("institution:org-2", "org-2", Set.of("INSTITUTION"));
    private final CurrentUser donor = new CurrentUser("donor:org-1", "org-1", Set.of("DONOR"));

    @Test
    void offerFromTheReadModelHidesInternalFieldsAndUsesPublicLinks() throws Exception {
        JsonNode source = mapper.readTree("""
                {"id":"offer-1","status":"AVAILABLE","donorName":"Mercado Central","lastEvent":"OfferCreated",
                 "eventOccurredAt":"x","projectedAt":"y",
                 "_links":{"self":{"href":"/api/v1/offers/offer-1","method":"GET"},
                           "reserve":{"href":"/api/v1/offers/offer-1/reservations","method":"POST"}}}
                """);

        ObjectNode forInstitution = views.offer(source, institution);
        ObjectNode forDonor = views.offer(source, donor);

        assertThat(forInstitution.has("lastEvent")).isFalse();
        assertThat(forInstitution.has("projectedAt")).isFalse();
        assertThat(forInstitution.path("donorName").asText()).isEqualTo("Mercado Central");
        assertThat(forInstitution.at("/_links/self/href").asText()).isEqualTo("/portal/v1/offers/offer-1");
        assertThat(forInstitution.at("/_links/start-redistribution/href").asText()).isEqualTo("/portal/v1/redistributions");
        assertThat(forInstitution.path("_links").has("reserve")).isFalse();
        assertThat(forDonor.path("_links").has("start-redistribution")).isFalse();
    }

    @Test
    void redistributionIsTaskOrientedAndHidesTheSagaInternals() throws Exception {
        JsonNode redistribution = mapper.readTree("""
                {"id":"red-1","offerId":"offer-1","institutionId":"org-2","reservationId":"res-1",
                 "logisticsId":"log-1","status":"IN_TRANSIT","lastEvent":"FoodCollected","correlationId":"corr-1",
                 "createdAt":"2026-11-25T10:00:00","updatedAt":"2026-11-25T10:05:00"}
                """);
        JsonNode offer = mapper.readTree("""
                {"id":"offer-1","donorId":"org-1","description":"Hortaliças variadas","foodCategory":"VEGETABLES",
                 "quantity":30,"unit":"KG","pickupAddress":{"city":"Lavras"}}
                """);
        JsonNode logistics = mapper.readTree("""
                {"id":"log-1","status":"COLLECTED","pickupAddress":"Rua Central, 100","deliveryAddress":"Rua das Flores, 50",
                 "collectedAt":"2026-11-25T10:05:00"}
                """);

        ObjectNode view = views.redistribution(redistribution, offer, logistics, institution);

        assertThat(view.path("statusDescription").asText()).isEqualTo("Doação coletada e a caminho");
        assertThat(view.at("/offer/description").asText()).isEqualTo("Hortaliças variadas");
        assertThat(view.at("/delivery/status").asText()).isEqualTo("COLLECTED");
        for (String internal : new String[] {"reservationId", "logisticsId", "lastEvent", "correlationId", "sagaState"}) {
            assertThat(view.has(internal)).as(internal).isFalse();
        }
        // já coletada: não pode mais ser cancelada pelo Portal
        assertThat(view.path("_links").has("cancel")).isFalse();
    }

    @Test
    void failedRedistributionExplainsTheProblemInPlainWords() throws Exception {
        JsonNode redistribution = mapper.readTree("""
                {"id":"red-2","offerId":"offer-1","status":"FAILED",
                 "failureReason":"Falha ao reservar a oferta no offer-service: 409 OFFER_NOT_AVAILABLE"}
                """);

        ObjectNode view = views.redistribution(redistribution, null, null, institution);

        assertThat(view.path("problem").asText()).isEqualTo("A oferta não está mais disponível.");
        assertThat(view.has("failureReason")).isFalse();
        assertThat(view.at("/delivery/status").asText()).isEqualTo("NOT_SCHEDULED");
    }

    @Test
    void springDataPageBecomesTheContractPage() throws Exception {
        JsonNode springPage = mapper.readTree("""
                {"content":[{"id":"dem-1"}],"number":0,"size":20,"totalElements":1}
                """);

        ObjectNode page = Json.page(mapper, springPage, views::demand);

        assertThat(page.path("page").asInt()).isEqualTo(1);
        assertThat(page.path("total").asLong()).isEqualTo(1);
        assertThat(page.at("/items/0/_links/matches/href").asText()).isEqualTo("/portal/v1/demands/dem-1/matches");
    }

    @Test
    void formatsAnAddressForTheLogistics() throws Exception {
        JsonNode address = mapper.readTree("""
                {"street":"Rua Central","number":"100","district":"Centro","city":"Lavras","state":"MG"}
                """);

        assertThat(Json.formatAddress(address)).isEqualTo("Rua Central, 100 - Centro, Lavras/MG");
    }
}
