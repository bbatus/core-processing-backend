package com.vodafone.genaiops.cpb.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vodafone.genaiops.cpb.dto.AiFetchRequest;
import com.vodafone.genaiops.cpb.entity.AiDispatch;
import com.vodafone.genaiops.cpb.entity.Ticket;
import com.vodafone.genaiops.cpb.entity.TicketContext;
import com.vodafone.genaiops.cpb.enums.TriggerRule;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContextRequestMapperTest {

    private final ContextRequestMapper mapper = new ContextRequestMapper(new ObjectMapper());

    @Test
    void gercekciContextJsondanDuzAlanlariDogruCikarir() {
        UUID dcaseTicketId = UUID.randomUUID();
        Ticket ticket = new Ticket();
        ticket.setDcaseTicketId(dcaseTicketId);
        ticket.setTicketNumber(79024L);

        TicketContext context = new TicketContext();
        context.setContextJson("""
                {
                  "ticket": {
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
                """);

        AiDispatch dispatch = new AiDispatch();
        dispatch.setVersion(1);
        dispatch.setTriggerRule(TriggerRule.R4);

        AiFetchRequest request = mapper.toRequest(ticket, context, dispatch, 1);

        assertThat(request.ticketId()).isEqualTo(dcaseTicketId.toString());
        assertThat(request.ticketNumber()).isEqualTo(79024L);
        assertThat(request.version()).isEqualTo(1);
        assertThat(request.iteration()).isEqualTo(1);
        assertThat(request.triggerRule()).isEqualTo("R4");
        assertThat(request.product()).isEqualTo("Cuzdan");
        assertThat(request.mainCategory()).isEqualTo("Iadeler");
        assertThat(request.subCategory()).isEqualTo("Yaptigim iade cuzdan bakiyeme dusmedi");
        assertThat(request.title()).isEqualTo("Iade bakiyeme yansimadi");
        assertThat(request.problemDescription()).isEqualTo("Musteri iadenin cuzdanina gecmedigini bildiriyor.");
        assertThat(request.msisdn()).isEqualTo("905906020100");
        assertThat(request.customerName()).isEqualTo("TEST2506");
        assertThat(request.priority()).isEqualTo("Orta");
        assertThat(request.humanFeedback()).isNull();
        assertThat(request.comments()).hasSize(1);
        assertThat(request.contextJson()).isNotNull();
    }

    @Test
    void r5TurundaHumanFeedbackDoluOlur() {
        Ticket ticket = new Ticket();
        ticket.setDcaseTicketId(UUID.randomUUID());
        TicketContext context = new TicketContext();
        context.setContextJson("""
                {"ticket":{"title":"x"},"processing":{"version":2,"humanFeedback":"Dogrudur, onayliyorum"}}
                """);
        AiDispatch dispatch = new AiDispatch();
        dispatch.setVersion(2);
        dispatch.setTriggerRule(TriggerRule.R5);

        AiFetchRequest request = mapper.toRequest(ticket, context, dispatch, 2);

        assertThat(request.humanFeedback()).isEqualTo("Dogrudur, onayliyorum");
        assertThat(request.triggerRule()).isEqualTo("R5");
    }

    @Test
    void bozukVeyaBosContextJsonPatlamazNullAlanlarDoner() {
        Ticket ticket = new Ticket();
        ticket.setDcaseTicketId(UUID.randomUUID());
        TicketContext context = new TicketContext();
        context.setContextJson(null);
        AiDispatch dispatch = new AiDispatch();
        dispatch.setVersion(1);
        dispatch.setTriggerRule(TriggerRule.R4);

        AiFetchRequest request = mapper.toRequest(ticket, context, dispatch, 1);

        assertThat(request.product()).isNull();
        assertThat(request.title()).isNull();
        assertThat(request.comments()).isEmpty();
    }
}
