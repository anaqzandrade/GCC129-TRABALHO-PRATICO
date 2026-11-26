package com.ravita.admin.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** O que o Admin mostra a mais que o Portal, para a mesma redistribuição. */
class AdminViewsTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AdminViews views = new AdminViews(mapper);

    @Test
    void detailExposesTheSagaInternalsForDiagnosis() throws Exception {
        JsonNode redistribution = mapper.readTree("""
                {"id":"red-1","offerId":"offer-1","institutionId":"org-2","reservationId":"res-1",
                 "logisticsId":"log-1","status":"IN_TRANSIT","lastEvent":"FoodCollected","correlationId":"corr-1"}
                """);
        JsonNode offer = mapper.readTree("""
                {"id":"offer-1","donorId":"org-1","status":"RESERVED","description":"Hortaliças variadas"}
                """);
        JsonNode logistics = mapper.readTree("""
                {"id":"log-1","status":"COLLECTED","collectedAt":"2026-11-25T10:05:00"}
                """);

        ObjectNode view = views.redistributionDetail(redistribution, offer, logistics);

        assertThat(view.path("sagaState").asText()).isEqualTo("IN_TRANSIT");
        assertThat(view.path("lastEvent").asText()).isEqualTo("FoodCollected");
        assertThat(view.path("correlationId").asText()).isEqualTo("corr-1");
        assertThat(view.path("donorId").asText()).isEqualTo("org-1");
        assertThat(view.path("reservationId").asText()).isEqualTo("res-1");
        assertThat(view.path("logisticsId").asText()).isEqualTo("log-1");
        assertThat(view.at("/logistics/status").asText()).isEqualTo("COLLECTED");
        assertThat(view.path("compensation").asText()).isEqualTo("NONE");
    }

    @Test
    void failedRedistributionShowsWhetherTheCompensationRan() throws Exception {
        JsonNode compensated = mapper.readTree("""
                {"id":"red-2","status":"FAILED","lastEvent":"ReservationReleased",
                 "failureReason":"Falha ao criar a operação logística; compensação executada"}
                """);
        JsonNode notReserved = mapper.readTree("""
                {"id":"red-3","status":"FAILED","lastEvent":"OfferReservationFailed"}
                """);

        assertThat(views.redistributionDetail(compensated, null, null).path("compensation").asText()).isEqualTo("DONE");
        assertThat(views.redistributionDetail(compensated, null, null).path("failureReason").asText())
                .contains("compensação executada");
        assertThat(views.redistributionDetail(notReserved, null, null).path("compensation").asText())
                .isEqualTo("NOT_NEEDED");
    }

    @Test
    void sagaStatesFollowDoc04() {
        assertThat(AdminViews.sagaState("LOGISTICS_PENDING")).isEqualTo("LOGISTICS_SCHEDULED");
        assertThat(AdminViews.sagaState("COMPLETED")).isEqualTo("COMPLETED");
    }

    @Test
    void dashboardCombinesThreeServices() throws Exception {
        JsonNode offers = mapper.readTree("""
                {"total":3,"counts":{"AVAILABLE":2,"RESERVED":1}}
                """);
        JsonNode redistributions = mapper.readTree("""
                {"total":2,"byStatus":{"COMPLETED":1,"FAILED":1}}
                """);

        ObjectNode view = views.dashboard(4, 2, offers, redistributions, ZoneId.of("America/Sao_Paulo"));

        assertThat(view.at("/organizations/donors").asLong()).isEqualTo(4);
        assertThat(view.at("/offers/byStatus/AVAILABLE").asLong()).isEqualTo(2);
        assertThat(view.at("/redistributions/byStatus/FAILED").asLong()).isEqualTo(1);
    }
}
