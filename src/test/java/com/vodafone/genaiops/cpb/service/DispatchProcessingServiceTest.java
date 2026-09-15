package com.vodafone.genaiops.cpb.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vodafone.genaiops.cpb.TestResponses;
import com.vodafone.genaiops.cpb.client.AiAgentClient;
import com.vodafone.genaiops.cpb.config.AiProperties;
import com.vodafone.genaiops.cpb.dto.AiCallResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vodafone.genaiops.cpb.dto.AiFetchResponse;
import com.vodafone.genaiops.cpb.entity.AiActionInbox;
import com.vodafone.genaiops.cpb.entity.AiDispatch;
import com.vodafone.genaiops.cpb.entity.AiProcess;
import com.vodafone.genaiops.cpb.entity.Ticket;
import com.vodafone.genaiops.cpb.entity.TicketContext;
import com.vodafone.genaiops.cpb.enums.AiProcessStatus;
import com.vodafone.genaiops.cpb.enums.DispatchStatus;
import com.vodafone.genaiops.cpb.enums.TriggerRule;
import com.vodafone.genaiops.cpb.mapper.ContextRequestMapper;
import com.vodafone.genaiops.cpb.repository.AiDispatchRepository;
import com.vodafone.genaiops.cpb.repository.AiInteractionRepository;
import com.vodafone.genaiops.cpb.repository.AiProcessRepository;
import com.vodafone.genaiops.cpb.repository.TicketContextRepository;
import com.vodafone.genaiops.cpb.repository.TicketRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DispatchProcessingServiceTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketContextRepository ticketContextRepository;
    @Mock
    private AiDispatchRepository aiDispatchRepository;
    @Mock
    private AiProcessRepository aiProcessRepository;
    @Mock
    private AiInteractionRepository aiInteractionRepository;
    @Mock
    private ContextRequestMapper contextRequestMapper;
    @Mock
    private AiAgentClient aiAgentClient;
    @Mock
    private ActionInboxWriter actionInboxWriter;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private FlowGuard flowGuard;
    @Mock
    private CpbMetrics cpbMetrics;

    private DispatchProcessingService service;
    private AiDispatch dispatch;
    private Ticket ticket;
    private TicketContext context;

    @BeforeEach
    void setUp() {
        AiProperties aiProperties = new AiProperties("http://ai", "/fetch", "/confirm", null, 5000, 180, 3, 5,
                "GenAI Ops", false);
        service = new DispatchProcessingService(ticketRepository, ticketContextRepository, aiDispatchRepository,
                aiProcessRepository, aiInteractionRepository, contextRequestMapper, aiAgentClient, actionInboxWriter,
                auditLogService, flowGuard, aiProperties, cpbMetrics);

        dispatch = new AiDispatch();
        dispatch.setId(1L);
        dispatch.setTicketId(10L);
        dispatch.setContextId(20L);
        dispatch.setVersion(1);
        dispatch.setTriggerRule(TriggerRule.R4);
        dispatch.setStatus(DispatchStatus.CLAIMED);

        ticket = new Ticket();
        ticket.setId(10L);
        ticket.setDcaseTicketId(UUID.randomUUID());

        context = new TicketContext();
        context.setId(20L);

        org.mockito.Mockito.lenient().when(aiDispatchRepository.findById(1L)).thenReturn(Optional.of(dispatch));
    }

    @Test
    void dispatchArtikClaimedDegilseSessizceAtlanir() {
        dispatch.setStatus(DispatchStatus.CANCELLED);

        service.process(1L);

        verify(ticketRepository, never()).findById(anyLong());
        verify(aiAgentClient, never()).fetchWithRetries(any());
    }

    @Test
    void dispatchBulunamazsaSessizceAtlanir() {
        when(aiDispatchRepository.findById(99L)).thenReturn(Optional.empty());

        service.process(99L);

        verify(aiAgentClient, never()).fetchWithRetries(any());
    }

    @Test
    void aiCallKapaliysaDispatchPendingeGeriDonerAiHicCagrilmaz() {
        when(flowGuard.isAiCallEnabled()).thenReturn(false);

        service.process(1L);

        assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.PENDING);
        assertThat(dispatch.getClaimedBy()).isNull();
        verify(aiAgentClient, never()).fetchWithRetries(any());
        verify(auditLogService).write(eq(com.vodafone.genaiops.cpb.enums.AuditCategory.CPB_SKIPPED_KILL_SWITCH),
                any(), any());
    }

    @Test
    void mutluYol_AiBasariliIseInboxYazilirVeDispatchTamamlanir() {
        when(flowGuard.isAiCallEnabled()).thenReturn(true);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketContextRepository.findById(20L)).thenReturn(Optional.of(context));
        when(aiProcessRepository.findByDispatchIdAndIteration(1L, 1)).thenReturn(Optional.empty());
        when(aiProcessRepository.save(any(AiProcess.class))).thenAnswer(inv -> {
            AiProcess p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(100L);
            }
            return p;
        });
        JsonNode request = sampleRequest();
        when(contextRequestMapper.toRequest(eq(ticket), eq(context), eq(dispatch), eq(1), any(), any())).thenReturn(request);
        AiFetchResponse response = TestResponses.proposal("sol-1", "cozum metni");
        AiCallResult okResult = new AiCallResult("{}", 200, "{}", response, null, 42L);
        when(aiAgentClient.fetchWithRetries(request)).thenReturn(List.of(okResult));
        AiActionInbox inbox = new AiActionInbox();
        inbox.setId(5L);
        when(actionInboxWriter.writeProposal(dispatch, ticket, response, false)).thenReturn(inbox);

        service.process(1L);

        assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.COMPLETED);
        assertThat(dispatch.getCompletedAt()).isNotNull();
        verify(actionInboxWriter).writeProposal(dispatch, ticket, response, false);
        verify(cpbMetrics).recordAiCallDuration(42L);
        verify(cpbMetrics).incrementInboxWritten();
        verify(cpbMetrics, never()).incrementAiCallFailure();

        ArgumentCaptor<AiProcess> processCaptor = ArgumentCaptor.forClass(AiProcess.class);
        verify(aiProcessRepository, times(2)).save(processCaptor.capture());
        AiProcess finalProcess = processCaptor.getAllValues().get(processCaptor.getAllValues().size() - 1);
        assertThat(finalProcess.getStatus()).isEqualTo(AiProcessStatus.SUCCEEDED);
        assertThat(finalProcess.getSolutionUniqueid()).isEqualTo("sol-1");
    }

    @Test
    void aiSonDenemedeBasarisizsaDispatchFailedOlurInboxYazilmaz() {
        when(flowGuard.isAiCallEnabled()).thenReturn(true);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketContextRepository.findById(20L)).thenReturn(Optional.of(context));
        when(aiProcessRepository.findByDispatchIdAndIteration(1L, 1)).thenReturn(Optional.empty());
        when(aiProcessRepository.save(any(AiProcess.class))).thenAnswer(inv -> {
            AiProcess p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(101L);
            }
            return p;
        });
        JsonNode request = sampleRequest();
        when(contextRequestMapper.toRequest(eq(ticket), eq(context), eq(dispatch), eq(1), any(), any())).thenReturn(request);
        AiCallResult failResult = new AiCallResult("{}", 500, null, null, "baglanti hatasi", 10L);
        when(aiAgentClient.fetchWithRetries(request)).thenReturn(List.of(failResult, failResult, failResult));

        service.process(1L);

        assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.FAILED);
        assertThat(dispatch.getErrorMessage()).isEqualTo("baglanti hatasi");
        verify(actionInboxWriter, never()).writeProposal(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(cpbMetrics).incrementAiCallFailure();
    }

    @Test
    void aiHttp200DonseDeStatusResultFAILUREiseDispatchFailedOlurInboxYazilmaz() {
        // Didar rehberi §8: AI, tasima katmani basarili olsa bile "statusResult=FAILURE" donebilir
        // (SOLUTION_ID_MISMATCH, SESSION_EXPIRED, AGENT_TIMEOUT...). Yazilacak bir oneri YOKTUR.
        when(flowGuard.isAiCallEnabled()).thenReturn(true);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketContextRepository.findById(20L)).thenReturn(Optional.of(context));
        when(aiProcessRepository.findByDispatchIdAndIteration(1L, 1)).thenReturn(Optional.empty());
        when(aiProcessRepository.save(any(AiProcess.class))).thenAnswer(inv -> {
            AiProcess p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(102L);
            }
            return p;
        });
        JsonNode request = sampleRequest();
        when(contextRequestMapper.toRequest(eq(ticket), eq(context), eq(dispatch), eq(1), any(), any()))
                .thenReturn(request);
        AiFetchResponse failure = TestResponses.failure("SOLUTION_ID_MISMATCH", "Oneri kimligi eslesmedi.");
        when(aiAgentClient.fetchWithRetries(request))
                .thenReturn(List.of(new AiCallResult("{}", 200, "{}", failure, null, 33L)));

        service.process(1L);

        assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.FAILED);
        assertThat(dispatch.getErrorMessage()).contains("SOLUTION_ID_MISMATCH");
        verify(actionInboxWriter, never()).writeProposal(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(cpbMetrics).incrementAiCallFailure();
        verify(cpbMetrics, never()).incrementInboxWritten();

        ArgumentCaptor<AiProcess> processCaptor = ArgumentCaptor.forClass(AiProcess.class);
        verify(aiProcessRepository, times(2)).save(processCaptor.capture());
        AiProcess finalProcess = processCaptor.getAllValues().get(processCaptor.getAllValues().size() - 1);
        assertThat(finalProcess.getStatus()).isEqualTo(AiProcessStatus.FAILED);
        assertThat(finalProcess.getErrorCode()).isEqualTo("SOLUTION_ID_MISMATCH");
        assertThat(finalProcess.getStatusResult()).isEqualTo("FAILURE");
    }

    @Test
    void r5TurundeOncekiAiSolutionIdBulunupMappereGecirilir() {
        // Didar rehberi §3 adim 6: "ayni ticket + ayni aiSolutionId" geri gonderilmeli.
        dispatch.setTriggerRule(TriggerRule.R5);
        when(flowGuard.isAiCallEnabled()).thenReturn(true);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketContextRepository.findById(20L)).thenReturn(Optional.of(context));
        when(aiProcessRepository.findByDispatchIdAndIteration(1L, 1)).thenReturn(Optional.empty());
        when(aiProcessRepository.save(any(AiProcess.class))).thenAnswer(inv -> {
            AiProcess p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(103L);
            }
            return p;
        });
        when(aiProcessRepository.findLatestAiSolutionId(10L)).thenReturn(Optional.of("onceki-sol-99"));
        JsonNode request = sampleRequest();
        when(contextRequestMapper.toRequest(eq(ticket), eq(context), eq(dispatch), eq(1),
                eq("onceki-sol-99"), any())).thenReturn(request);
        AiFetchResponse response = TestResponses.noActionNeeded("sol-2", "Ek aksiyon gerekmiyor");
        when(aiAgentClient.fetchWithRetries(request))
                .thenReturn(List.of(new AiCallResult("{}", 200, "{}", response, null, 20L)));
        when(actionInboxWriter.writeProposal(dispatch, ticket, response, false)).thenReturn(new AiActionInbox());

        service.process(1L);

        // Onceki tur kimligi mapper'a GECIRILDI (eq("onceki-sol-99") eslesmeseydi stub calismazdi).
        verify(contextRequestMapper).toRequest(eq(ticket), eq(context), eq(dispatch), eq(1),
                eq("onceki-sol-99"), any());
        assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.COMPLETED);
    }

    @Test
    void r4TurundeOncekiAiSolutionIdHicARANMAZ_nullGecirilir() {
        // R4 ilk turdur; onceki bir oneri yoktur, gereksiz sorgu atilmamalidir.
        when(flowGuard.isAiCallEnabled()).thenReturn(true);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketContextRepository.findById(20L)).thenReturn(Optional.of(context));
        when(aiProcessRepository.findByDispatchIdAndIteration(1L, 1)).thenReturn(Optional.empty());
        when(aiProcessRepository.save(any(AiProcess.class))).thenAnswer(inv -> {
            AiProcess p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(104L);
            }
            return p;
        });
        JsonNode request = sampleRequest();
        when(contextRequestMapper.toRequest(eq(ticket), eq(context), eq(dispatch), eq(1), eq(null), any()))
                .thenReturn(request);
        AiFetchResponse response = TestResponses.proposal("sol-3", "oneri");
        when(aiAgentClient.fetchWithRetries(request))
                .thenReturn(List.of(new AiCallResult("{}", 200, "{}", response, null, 15L)));
        when(actionInboxWriter.writeProposal(dispatch, ticket, response, false)).thenReturn(new AiActionInbox());

        service.process(1L);

        verify(aiProcessRepository, never()).findLatestAiSolutionId(anyLong());
    }

    @Test
    void maxIterationsUlasildiysaAiHicCagrilmazDogrudanKapatilir() {
        dispatch.setVersion(6); // maxIterations=5, 6 > 5
        when(flowGuard.isAiCallEnabled()).thenReturn(true);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketContextRepository.findById(20L)).thenReturn(Optional.of(context));
        when(aiProcessRepository.findByDispatchIdAndIteration(1L, 6)).thenReturn(Optional.empty());
        when(aiProcessRepository.save(any(AiProcess.class))).thenAnswer(inv -> {
            AiProcess p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(102L);
            }
            return p;
        });
        AiActionInbox inbox = new AiActionInbox();
        when(actionInboxWriter.writeProposal(dispatch, ticket, null, true)).thenReturn(inbox);

        service.process(1L);

        verify(aiAgentClient, never()).fetchWithRetries(any());
        assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.COMPLETED);
        verify(auditLogService).write(
                eq(com.vodafone.genaiops.cpb.enums.AuditCategory.CPB_MAX_ITERATIONS_REACHED), any(), any());
    }

    /** C5.3 regresyon testi — sahada bulunan gercek bug: zaten SUCCEEDED bir surec icin AI tekrar cagrilmamali. */
    @Test
    void zatenBasariylaSonuclanmisSurecIcinAiTekrarCagrilmazDispatchSenkronlanir() {
        when(flowGuard.isAiCallEnabled()).thenReturn(true);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketContextRepository.findById(20L)).thenReturn(Optional.of(context));
        AiProcess existingProcess = new AiProcess();
        existingProcess.setId(200L);
        existingProcess.setStatus(AiProcessStatus.SUCCEEDED);
        when(aiProcessRepository.findByDispatchIdAndIteration(1L, 1)).thenReturn(Optional.of(existingProcess));

        service.process(1L);

        verify(aiAgentClient, never()).fetchWithRetries(any());
        verify(actionInboxWriter, never()).writeProposal(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.COMPLETED);
    }

    /** Ayni regresyonun FAILED varyanti. */
    @Test
    void zatenBasarisizSonuclanmisSurecIcinAiTekrarCagrilmazDispatchFailedSenkronlanir() {
        when(flowGuard.isAiCallEnabled()).thenReturn(true);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketContextRepository.findById(20L)).thenReturn(Optional.of(context));
        AiProcess existingProcess = new AiProcess();
        existingProcess.setId(201L);
        existingProcess.setStatus(AiProcessStatus.FAILED);
        existingProcess.setErrorMessage("onceki hata");
        when(aiProcessRepository.findByDispatchIdAndIteration(1L, 1)).thenReturn(Optional.of(existingProcess));

        service.process(1L);

        verify(aiAgentClient, never()).fetchWithRetries(any());
        assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.FAILED);
        assertThat(dispatch.getErrorMessage()).isEqualTo("onceki hata");
    }

    private JsonNode sampleRequest() {
        return new ObjectMapper().createObjectNode().put("schemaVersion", 1);
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
