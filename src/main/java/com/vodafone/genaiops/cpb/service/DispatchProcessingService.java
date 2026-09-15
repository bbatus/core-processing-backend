package com.vodafone.genaiops.cpb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.vodafone.genaiops.cpb.client.AiAgentClient;
import com.vodafone.genaiops.cpb.config.AiProperties;
import com.vodafone.genaiops.cpb.dto.AiCallResult;
import com.vodafone.genaiops.cpb.dto.AiFetchResponse;
import com.vodafone.genaiops.cpb.entity.AiDispatch;
import com.vodafone.genaiops.cpb.entity.AiInteraction;
import com.vodafone.genaiops.cpb.entity.AiProcess;
import com.vodafone.genaiops.cpb.entity.Ticket;
import com.vodafone.genaiops.cpb.entity.TicketContext;
import com.vodafone.genaiops.cpb.enums.AiProcessStatus;
import com.vodafone.genaiops.cpb.enums.AuditCategory;
import com.vodafone.genaiops.cpb.enums.DispatchStatus;
import com.vodafone.genaiops.cpb.enums.TriggerRule;
import com.vodafone.genaiops.cpb.mapper.ContextRequestMapper;
import com.vodafone.genaiops.cpb.repository.AiDispatchRepository;
import com.vodafone.genaiops.cpb.repository.AiInteractionRepository;
import com.vodafone.genaiops.cpb.repository.AiProcessRepository;
import com.vodafone.genaiops.cpb.repository.TicketContextRepository;
import com.vodafone.genaiops.cpb.repository.TicketRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bir dispatch'in tam islenmesini yonetir: context'i AI istegine cevirir, AI Agent'i cagirir, her
 * denemeyi ({@code ai_interaction}) ve tur ozetini ({@code ai_process}) kaydeder, sonucu
 * {@code ai_action_inbox}'a yazar, {@code ai_dispatch} durumunu kapatir. Bkz.
 * CPB_TASARIM_VE_GELISTIRME_PLANI §3.2/§9.3.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchProcessingService {

    private static final String DISPATCH_ID = "dispatchId";

    private final TicketRepository ticketRepository;
    private final TicketContextRepository ticketContextRepository;
    private final AiDispatchRepository aiDispatchRepository;
    private final AiProcessRepository aiProcessRepository;
    private final AiInteractionRepository aiInteractionRepository;
    private final ContextRequestMapper contextRequestMapper;
    private final AiAgentClient aiAgentClient;
    private final ActionInboxWriter actionInboxWriter;
    private final AuditLogService auditLogService;
    private final FlowGuard flowGuard;
    private final AiProperties aiProperties;
    private final CpbMetrics cpbMetrics;

    @Transactional
    public void process(Long dispatchId) {
        MDC.put(DISPATCH_ID, String.valueOf(dispatchId));
        MDC.put("correlationId", UUID.randomUUID().toString());
        try {
            doProcess(dispatchId);
        } finally {
            MDC.clear();
        }
    }

    private void doProcess(Long dispatchId) {
        AiDispatch dispatch = aiDispatchRepository.findById(dispatchId).orElse(null);
        if (dispatch == null || dispatch.getStatus() != DispatchStatus.CLAIMED) {
            // EP tarafinda arada CANCELLED/OBSOLETE olmus olabilir (ticket baska gruba gecti vb.) —
            // bkz. §9.3: bu durumda DCase'e bosuna yorum yazilmamasi icin sessizce atlanir.
            log.info("Dispatch artik CLAIMED degil, atlaniyor: dispatchId={}", dispatchId);
            return;
        }
        MDC.put("version", String.valueOf(dispatch.getVersion()));

        if (!flowGuard.isAiCallEnabled()) {
            revertToPending(dispatch);
            auditLogService.write(AuditCategory.CPB_SKIPPED_KILL_SWITCH, ticketDcaseId(dispatch),
                    Map.of(DISPATCH_ID, dispatch.getId(), "reason", "FLOW_AI_CALL_ENABLED=false"));
            return;
        }

        Ticket ticket = ticketRepository.findById(dispatch.getTicketId()).orElseThrow();
        TicketContext context = ticketContextRepository.findById(dispatch.getContextId()).orElseThrow();
        int iteration = dispatch.getVersion();
        MDC.put("dcaseTicketId", String.valueOf(ticket.getDcaseTicketId()));
        MDC.put("iteration", String.valueOf(iteration));
        auditLogService.write(AuditCategory.CPB_DISPATCH_CLAIMED, ticket.getDcaseTicketId(),
                Map.of(DISPATCH_ID, dispatch.getId(), "triggerRule", String.valueOf(dispatch.getTriggerRule())));
        boolean maxIterationsReached = aiProperties.maxIterations() > 0 && iteration > aiProperties.maxIterations();

        AiProcess process = startProcess(dispatch, ticket, iteration);
        if (process.getStatus() != AiProcessStatus.IN_PROGRESS) {
            // Bu dispatch/iteration icin daha once TAMAMLANMIS bir surec var (orn. dispatch elle ya
            // da bir hata sonrasi tekrar PENDING'e dusmus) — AI Agent'i GEREKSIZ YERE tekrar cagirmak
            // yerine (pahali + §6.6'nin ruhuna aykiri) yalnizca dispatch'i o sonuca gore senkron eder.
            log.info("Bu dispatch/iteration icin AiProcess zaten sonuclanmis, AI tekrar cagrilmiyor: "
                    + "dispatchId={}, processStatus={}", dispatch.getId(), process.getStatus());
            if (process.getStatus() == AiProcessStatus.SUCCEEDED) {
                completeDispatch(dispatch);
            } else {
                failDispatch(dispatch, process.getErrorMessage());
            }
            return;
        }

        if (maxIterationsReached) {
            actionInboxWriter.writeProposal(dispatch, ticket, null, true);
            cpbMetrics.incrementInboxWritten();
            finishProcess(process, AiProcessStatus.SUCCEEDED, null, "MAX_ITERATIONS_REACHED");
            completeDispatch(dispatch);
            auditLogService.write(AuditCategory.CPB_MAX_ITERATIONS_REACHED, ticket.getDcaseTicketId(),
                    Map.of(DISPATCH_ID, dispatch.getId(), "iteration", iteration));
            return;
        }

        // R5/R6'da AI'a onceki turun oneri kimligi geri gonderilir ("eslesme kontrolu", rehber §3
        // adim 6-7). R4 ilk tur oldugu icin null gider.
        String previousAiSolutionId = dispatch.getTriggerRule() == TriggerRule.R4
                ? null
                : aiProcessRepository.findLatestAiSolutionId(dispatch.getTicketId()).orElse(null);
        JsonNode request = contextRequestMapper.toRequest(ticket, context, dispatch, iteration,
                previousAiSolutionId, MDC.get("correlationId"));
        List<AiCallResult> attempts = aiAgentClient.fetchWithRetries(request);
        persistInteractions(process, attempts);

        AiCallResult last = attempts.get(attempts.size() - 1);
        cpbMetrics.recordAiCallDuration(last.durationMs());
        if (!last.success()) {
            cpbMetrics.incrementAiCallFailure();
            finishProcess(process, AiProcessStatus.FAILED, null, last.errorMessage());
            failDispatch(dispatch, last.errorMessage());
            auditLogService.write(AuditCategory.CPB_AI_CALL_FAILED, ticket.getDcaseTicketId(),
                    Map.of(DISPATCH_ID, dispatch.getId(), "error", String.valueOf(last.errorMessage())));
            return;
        }

        AiFetchResponse response = last.response();
        recordResponseMetadata(process, response);

        // ⚠️ 2026-09-15: AI "HTTP 200 + statusResult=FAILURE" donebilir (rehber §8) — ornegin
        // SOLUTION_ID_MISMATCH ya da SESSION_EXPIRED. Bu, tasima katmani basarili olsa bile ISLEV
        // olarak basarisizliktir: DCase'e yazilacak bir oneri YOKTUR, inbox'a kayit ACILMAZ.
        if (response.isFailure()) {
            cpbMetrics.incrementAiCallFailure();
            finishProcess(process, AiProcessStatus.FAILED, null, response.failureSummary());
            failDispatch(dispatch, response.failureSummary());
            auditLogService.write(AuditCategory.CPB_AI_RETURNED_FAILURE, ticket.getDcaseTicketId(),
                    Map.of(DISPATCH_ID, dispatch.getId(), "errorCode", String.valueOf(response.errorCode()),
                            "message", String.valueOf(response.message())));
            return;
        }

        actionInboxWriter.writeProposal(dispatch, ticket, response, false);
        cpbMetrics.incrementInboxWritten();
        finishProcess(process, AiProcessStatus.SUCCEEDED, response.aiSolutionId(), response.solution());
        completeDispatch(dispatch);
        auditLogService.write(AuditCategory.CPB_AI_CALL_SUCCEEDED, ticket.getDcaseTicketId(),
                Map.of(DISPATCH_ID, dispatch.getId(), "aiSolutionId", String.valueOf(response.aiSolutionId()),
                        "statusResult", String.valueOf(response.statusResult())));
        auditLogService.write(AuditCategory.CPB_INBOX_WRITTEN, ticket.getDcaseTicketId(),
                Map.of(DISPATCH_ID, dispatch.getId()));
    }

    /** AI yanitinin denetim alanlarini ({@code statusResult}/{@code errorCode}/{@code transactionId})
     * {@code ai_process}'e yazar — basarili da olsa basarisiz da olsa (V2 migration, rehber §7). */
    private void recordResponseMetadata(AiProcess process, AiFetchResponse response) {
        process.setStatusResult(response.statusResult());
        process.setErrorCode(response.errorCode());
        process.setTransactionId(response.transactionId());
        process.setAiStatus(response.statusResult());
    }

    private AiProcess startProcess(AiDispatch dispatch, Ticket ticket, int iteration) {
        return aiProcessRepository.findByDispatchIdAndIteration(dispatch.getId(), iteration).orElseGet(() -> {
            AiProcess process = new AiProcess();
            process.setDispatchId(dispatch.getId());
            process.setTicketId(dispatch.getTicketId());
            process.setDcaseTicketId(ticket.getDcaseTicketId());
            process.setVersion(dispatch.getVersion());
            process.setIteration(iteration);
            process.setTriggerRule(dispatch.getTriggerRule());
            process.setStatus(AiProcessStatus.IN_PROGRESS);
            process.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
            return aiProcessRepository.save(process);
        });
    }

    private void persistInteractions(AiProcess process, List<AiCallResult> attempts) {
        for (int i = 0; i < attempts.size(); i++) {
            AiCallResult attempt = attempts.get(i);
            AiInteraction interaction = new AiInteraction();
            interaction.setProcessId(process.getId());
            interaction.setEndpoint("fetch");
            interaction.setHttpMethod("POST");
            interaction.setAttemptNo(i + 1);
            interaction.setRequestBody(attempt.requestBodyJson());
            interaction.setResponseStatus(attempt.httpStatus());
            interaction.setResponseBody(attempt.responseBodyRaw());
            interaction.setErrorMessage(attempt.errorMessage());
            interaction.setDurationMs(attempt.durationMs());
            interaction.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
            aiInteractionRepository.save(interaction);
        }
    }

    private void finishProcess(AiProcess process, AiProcessStatus status, String solutionUniqueid,
            String solutionOrError) {
        process.setStatus(status);
        process.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
        process.setDurationMs(java.time.Duration.between(process.getStartedAt(), process.getFinishedAt()).toMillis());
        if (status == AiProcessStatus.FAILED) {
            process.setErrorMessage(solutionOrError);
        } else {
            process.setSolutionUniqueid(solutionUniqueid);
            process.setSolutionText(solutionOrError);
        }
        aiProcessRepository.save(process);
    }

    private void completeDispatch(AiDispatch dispatch) {
        dispatch.setStatus(DispatchStatus.COMPLETED);
        dispatch.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
        aiDispatchRepository.save(dispatch);
    }

    private void failDispatch(AiDispatch dispatch, String errorMessage) {
        dispatch.setStatus(DispatchStatus.FAILED);
        dispatch.setErrorMessage(errorMessage);
        aiDispatchRepository.save(dispatch);
    }

    private void revertToPending(AiDispatch dispatch) {
        dispatch.setStatus(DispatchStatus.PENDING);
        dispatch.setClaimedBy(null);
        dispatch.setClaimedAt(null);
        aiDispatchRepository.save(dispatch);
    }

    private java.util.UUID ticketDcaseId(AiDispatch dispatch) {
        return ticketRepository.findById(dispatch.getTicketId()).map(Ticket::getDcaseTicketId).orElse(null);
    }
}
