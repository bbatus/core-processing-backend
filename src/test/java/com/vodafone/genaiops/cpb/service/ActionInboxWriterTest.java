package com.vodafone.genaiops.cpb.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vodafone.genaiops.cpb.TestResponses;
import com.vodafone.genaiops.cpb.config.AiProperties;
import com.vodafone.genaiops.cpb.dto.AiFetchResponse;
import com.vodafone.genaiops.cpb.entity.AiActionInbox;
import com.vodafone.genaiops.cpb.entity.AiDispatch;
import com.vodafone.genaiops.cpb.entity.Ticket;
import com.vodafone.genaiops.cpb.enums.TriggerRule;
import com.vodafone.genaiops.cpb.repository.AiActionInboxRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActionInboxWriterTest {

    @Mock
    private AiActionInboxRepository aiActionInboxRepository;

    private ActionInboxWriter writer;
    private AiDispatch dispatch;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        AiProperties aiProperties = new AiProperties("http://ai", "/fetch", "/confirm", "key",
                5000, 180, 3, 5, "GenAI Ops", false);
        writer = new ActionInboxWriter(aiActionInboxRepository, aiProperties, new ObjectMapper());

        dispatch = new AiDispatch();
        dispatch.setId(42L);
        dispatch.setTicketId(7L);
        dispatch.setVersion(1);
        dispatch.setTriggerRule(TriggerRule.R4);

        ticket = new Ticket();
        ticket.setId(7L);

        org.mockito.Mockito.lenient().when(aiActionInboxRepository.findBySourceMessageId(any()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(aiActionInboxRepository.save(any(AiActionInbox.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void r4IlkTurHerZamanOneriVeOnayGerektirir() {
        AiFetchResponse response = TestResponses.proposal("sol-1", "Cozum metni");

        AiActionInbox inbox = writer.writeProposal(dispatch, ticket, response, false);

        assertThat(inbox.getActionType()).isEqualTo("PROPOSE_RESOLUTION");
        assertThat(inbox.isRequiresApproval()).isTrue();
        assertThat(inbox.getDcaseUpdatePayload()).contains("Cozum metni");
    }

    @Test
    void r5AiNoActionNeededDersekapanisOlur() {
        dispatch.setTriggerRule(TriggerRule.R5);
        AiFetchResponse response = TestResponses.noActionNeeded("sol-2", "Ek aksiyon gerekmiyor");

        AiActionInbox inbox = writer.writeProposal(dispatch, ticket, response, false);

        assertThat(inbox.getActionType()).isEqualTo("NO_ACTION_NEEDED");
        assertThat(inbox.isRequiresApproval()).isFalse();
    }

    @Test
    void requiresApprovalHicGelmezseGUVENLIVARSAYILAN_onayGerekir() {
        // Faz 2 guvenlik kurali: belirsizlikte insana git, asla kendi basina kapatma.
        dispatch.setTriggerRule(TriggerRule.R5);
        AiFetchResponse response = TestResponses.withoutRequiresApproval("sol-7", "Belirsiz yanit");

        AiActionInbox inbox = writer.writeProposal(dispatch, ticket, response, false);

        assertThat(inbox.getActionType()).isEqualTo("PROPOSE_RESOLUTION");
        assertThat(inbox.isRequiresApproval()).isTrue();
    }

    @Test
    void r4HerZamanOnayGerektirir_AIrequiresApprovalFalseDeseBile() {
        // Didar rehberi §12 senaryo 2'yi BILEREK uygulamiyoruz — gerekce: ActionInboxWriter.decide()
        // javadoc'u (Faz 2'de ilk tur her zaman insana ugrar). Karar SD/urun onayi bekliyor.
        AiFetchResponse response = TestResponses.noActionNeeded("sol-8", "Aksiyon gerekmiyor");

        AiActionInbox inbox = writer.writeProposal(dispatch, ticket, response, false);

        assertThat(inbox.getActionType()).isEqualTo("PROPOSE_RESOLUTION");
        assertThat(inbox.isRequiresApproval()).isTrue();
    }

    @Test
    void r5StatusBelirsizsePropiseResolutionOlarakDevamEder() {
        dispatch.setTriggerRule(TriggerRule.R5);
        AiFetchResponse response = TestResponses.proposal("sol-3", "Duzeltilmis oneri");

        AiActionInbox inbox = writer.writeProposal(dispatch, ticket, response, false);

        assertThat(inbox.getActionType()).isEqualTo("PROPOSE_RESOLUTION");
        assertThat(inbox.isRequiresApproval()).isTrue();
    }

    @Test
    void maxIterationsReachedKapanisUretir() {
        AiActionInbox inbox = writer.writeProposal(dispatch, ticket, null, true);

        assertThat(inbox.getActionType()).isEqualTo("MAX_ITERATIONS_REACHED");
        assertThat(inbox.isRequiresApproval()).isFalse();
        assertThat(inbox.getDcaseUpdatePayload()).contains("Maksimum AI tur sayısına");
    }

    @Test
    void onayGerekiyorVeOncekiInsanAssigneeVarsaRelatedPartyEklenir() {
        UUID prevId = UUID.randomUUID();
        ticket.setPreviousHumanAssigneeId(prevId);
        ticket.setPreviousHumanAssigneeName("Huseyin Yakut");
        AiFetchResponse response = TestResponses.proposal("sol-4", "Oneri");

        AiActionInbox inbox = writer.writeProposal(dispatch, ticket, response, false);

        assertThat(inbox.getDcaseUpdatePayload()).contains("relatedParty");
        assertThat(inbox.getDcaseUpdatePayload()).contains(prevId.toString());
        assertThat(inbox.getDcaseUpdatePayload()).contains("Huseyin Yakut");
    }

    @Test
    void oncekiInsanAssigneeYoksaRelatedPartyEklenmez() {
        AiFetchResponse response = TestResponses.proposal("sol-5", "Oneri");

        AiActionInbox inbox = writer.writeProposal(dispatch, ticket, response, false);

        assertThat(inbox.getDcaseUpdatePayload()).doesNotContain("relatedParty");
    }

    @Test
    void onayGerekmiyorsaOncekiAssigneeVarsaBileRelatedPartyEklenmez() {
        dispatch.setTriggerRule(TriggerRule.R5);
        ticket.setPreviousHumanAssigneeId(UUID.randomUUID());
        ticket.setPreviousHumanAssigneeName("Huseyin Yakut");
        AiFetchResponse response = TestResponses.noActionNeeded("sol-6", "Kapaniyor");

        AiActionInbox inbox = writer.writeProposal(dispatch, ticket, response, false);

        assertThat(inbox.isRequiresApproval()).isFalse();
        assertThat(inbox.getDcaseUpdatePayload()).doesNotContain("relatedParty");
    }

    @Test
    void ayniDispatchIcinIkinciCagriMevcutKaydiDonerYeniSaveOlmaz() {
        AiActionInbox existing = new AiActionInbox();
        existing.setId(99L);
        when(aiActionInboxRepository.findBySourceMessageId(any())).thenReturn(Optional.of(existing));

        AiActionInbox result = writer.writeProposal(dispatch, ticket, TestResponses.proposal("s", "t"),
                false);

        assertThat(result).isSameAs(existing);
        verify(aiActionInboxRepository, never()).save(any());
    }

    @Test
    void ayniDispatchIciniIdempotentSourceMessageIdUretir() {
        writer.writeProposal(dispatch, ticket, TestResponses.proposal("s", "t"), false);
        writer.writeProposal(dispatch, ticket, TestResponses.proposal("s2", "t2"), false);

        var captor = org.mockito.ArgumentCaptor.forClass(UUID.class);
        verify(aiActionInboxRepository, times(2)).findBySourceMessageId(captor.capture());
        assertThat(captor.getAllValues().get(0)).isEqualTo(captor.getAllValues().get(1));
    }
}
