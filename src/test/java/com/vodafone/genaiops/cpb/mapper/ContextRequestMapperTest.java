package com.vodafone.genaiops.cpb.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vodafone.genaiops.cpb.entity.AiDispatch;
import com.vodafone.genaiops.cpb.entity.Ticket;
import com.vodafone.genaiops.cpb.entity.TicketContext;
import com.vodafone.genaiops.cpb.enums.TriggerRule;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 2026-09-15 "tek sema" sozlesmesi — EP'nin nested {@code context_json}'i BIREBIR gecirilir, uzerine
 * yalnizca CPB zarf alanlari eklenir (bkz. {@link ContextRequestMapper} javadoc'u).
 */
class ContextRequestMapperTest {

    private static final String CONTEXT_JSON = """
            {
              "schemaVersion": 1,
              "triggerRule": "R4",
              "ticket": {
                "dcaseTicketId": "b2f1c9a0-1111-4a2b-9c3d-000000000099",
                "title": "Iade bakiyeme yansimadi",
                "description": "Musteri iadenin cuzdanina gecmedigini bildiriyor.",
                "phoneNumber": "905906020100",
                "priority": "Orta",
                "category": {
                  "product": "Cuzdan",
                  "mainCategory": "Iadeler",
                  "subCategory": "Yaptigim iade cuzdan bakiyeme dusmedi"
                },
                "customer": { "fullName": "TEST2506" }
              },
              "comments": [ { "author": "Huseyin", "text": "kontrol ediyorum" } ],
              "processing": { "version": 1, "humanFeedback": null }
            }
            """;

    private final ContextRequestMapper mapper = new ContextRequestMapper(new ObjectMapper());

    private static Ticket ticket(UUID dcaseTicketId) {
        Ticket ticket = new Ticket();
        ticket.setDcaseTicketId(dcaseTicketId);
        ticket.setTicketNumber(79024L);
        return ticket;
    }

    private static TicketContext context(String json) {
        TicketContext context = new TicketContext();
        context.setContextJson(json);
        return context;
    }

    private static AiDispatch dispatch(int version, TriggerRule rule) {
        AiDispatch dispatch = new AiDispatch();
        dispatch.setVersion(version);
        dispatch.setTriggerRule(rule);
        return dispatch;
    }

    @Test
    void epNinNestedSemasiBIREBIRKorunur_duzAlanlaraCEVRILMEZ() {
        JsonNode request = mapper.toRequest(ticket(UUID.randomUUID()), context(CONTEXT_JSON),
                dispatch(1, TriggerRule.R4), 1, null, "trace-1");

        // Nested yapi aynen duruyor (Didar rehberi §4 bekledigi sekil).
        assertThat(request.path("ticket").path("title").asText()).isEqualTo("Iade bakiyeme yansimadi");
        assertThat(request.path("ticket").path("category").path("product").asText()).isEqualTo("Cuzdan");
        assertThat(request.path("ticket").path("category").path("mainCategory").asText()).isEqualTo("Iadeler");
        assertThat(request.path("ticket").path("category").path("subCategory").asText())
                .isEqualTo("Yaptigim iade cuzdan bakiyeme dusmedi");
        assertThat(request.path("comments")).hasSize(1);

        // Eski surumdeki duz (snake_case) kopyalar ARTIK YOK — "iki paralel sema" bulgusu kapandi.
        assertThat(request.has("ticket_id")).isFalse();
        assertThat(request.has("main_category")).isFalse();
        assertThat(request.has("msisdn")).isFalse();
        assertThat(request.has("context_json")).isFalse();
    }

    @Test
    void cpbZarfAlanlariEklenir() {
        UUID dcaseTicketId = UUID.randomUUID();

        JsonNode request = mapper.toRequest(ticket(dcaseTicketId), context(CONTEXT_JSON),
                dispatch(1, TriggerRule.R4), 1, null, "trace-42");

        assertThat(request.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(request.path("triggerRule").asText()).isEqualTo("R4");
        assertThat(request.path("traceId").asText()).isEqualTo("trace-42");
        assertThat(request.path("idempotencyKey").asText()).isEqualTo(dcaseTicketId + ":1:1");
        assertThat(request.path("processing").path("iteration").asInt()).isEqualTo(1);
    }

    @Test
    void r4TurundeAiSolutionIdNullGonderilir() {
        JsonNode request = mapper.toRequest(ticket(UUID.randomUUID()), context(CONTEXT_JSON),
                dispatch(1, TriggerRule.R4), 1, null, "trace-1");

        assertThat(request.path("processing").has("aiSolutionId")).isTrue();
        assertThat(request.path("processing").path("aiSolutionId").isNull()).isTrue();
    }

    @Test
    void r5TurundeOncekiAiSolutionIdGeriGonderilir() {
        String contextV2 = """
                {"schemaVersion":1,"ticket":{"title":"x"},
                 "processing":{"version":2,"humanFeedback":"Dogrudur, onayliyorum"}}
                """;

        JsonNode request = mapper.toRequest(ticket(UUID.randomUUID()), context(contextV2),
                dispatch(2, TriggerRule.R5), 2, "4873291123456789012", "trace-2");

        assertThat(request.path("triggerRule").asText()).isEqualTo("R5");
        assertThat(request.path("processing").path("aiSolutionId").asText()).isEqualTo("4873291123456789012");
        assertThat(request.path("processing").path("humanFeedback").asText()).isEqualTo("Dogrudur, onayliyorum");
        assertThat(request.path("processing").path("iteration").asInt()).isEqualTo(2);
    }

    @Test
    void triggerRuleDispatchtenYazilir_contextJsondakiEskiDegeriEzer() {
        // context_json R4 diyor ama bu cagriyi tetikleyen dispatch R5 — operatif olan dispatch'tir.
        JsonNode request = mapper.toRequest(ticket(UUID.randomUUID()), context(CONTEXT_JSON),
                dispatch(2, TriggerRule.R5), 2, "sol-1", "trace-3");

        assertThat(request.path("triggerRule").asText()).isEqualTo("R5");
    }

    @Test
    void bozukVeyaBosContextJsonPatlamaz_zarfYineDeUretilir() {
        UUID dcaseTicketId = UUID.randomUUID();

        JsonNode nullContext = mapper.toRequest(ticket(dcaseTicketId), context(null),
                dispatch(1, TriggerRule.R4), 1, null, "trace-4");
        JsonNode brokenContext = mapper.toRequest(ticket(dcaseTicketId), context("{bozuk json"),
                dispatch(1, TriggerRule.R4), 1, null, "trace-4");

        for (JsonNode request : new JsonNode[] { nullContext, brokenContext }) {
            assertThat(request.path("schemaVersion").asInt()).isEqualTo(1);
            assertThat(request.path("traceId").asText()).isEqualTo("trace-4");
            assertThat(request.path("idempotencyKey").asText()).isEqualTo(dcaseTicketId + ":1:1");
            assertThat(request.path("ticket").isMissingNode()).isTrue();
        }
    }
}
