package com.vodafone.genaiops.cpb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.vodafone.genaiops.cpb.entity.AiActionInbox;
import com.vodafone.genaiops.cpb.entity.AiDispatch;
import com.vodafone.genaiops.cpb.enums.ActionInboxStatus;
import com.vodafone.genaiops.cpb.enums.DispatchStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * TC-C1/TC-C2/TC-C6 — gerçek PostgreSQL + WireMock (AI Agent) ile CPB'nin dispatch işleme zincirini
 * (poller → AI çağrısı → ai_process/ai_interaction → ai_action_inbox → dispatch kapanışı) uçtan uca
 * doğrular. `DispatchPollerScheduler` gerçek zamanlayıcıyla (500ms) çalışır — Awaitility ile beklenir.
 */
class DispatchProcessingIntegrationTest extends AbstractIntegrationTest {

    @Test
    void tcC1_mutluYol_dispatchClaimEdilirAiCagrilirInboxYazilirDispatchTamamlanir() {
        UUID dcaseTicketId = UUID.randomUUID();
        stubAiAgentSuccess("sol-it-1", "Tespit ve oneri metni", true);
        Long dispatchId = seedTicketContextDispatch(dcaseTicketId, 1, "R4");

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            AiDispatch dispatch = aiDispatchRepository.findById(dispatchId).orElseThrow();
            assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.COMPLETED);
        });

        List<AiActionInbox> inbox = aiActionInboxRepository.findAll();
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).getStatus()).isEqualTo(ActionInboxStatus.PENDING);
        assertThat(inbox.get(0).isRequiresApproval()).isTrue();
        assertThat(inbox.get(0).getDcaseUpdatePayload()).contains("Tespit ve oneri metni");

        assertThat(aiProcessRepository.findAll()).hasSize(1);
        assertThat(aiInteractionRepository.findAll()).hasSize(1);
        assertThat(auditLogRepository.findAll()).isNotEmpty();
    }

    @Test
    void tcC2_aiSurekli500Doner_ucDenemeSonrasiDispatchFailedOlurInboxYazilmaz() {
        UUID dcaseTicketId = UUID.randomUUID();
        AI_AGENT.stubFor(post(urlPathEqualTo(fetchPath())).willReturn(aResponse().withStatus(500)));
        Long dispatchId = seedTicketContextDispatch(dcaseTicketId, 1, "R4");

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            AiDispatch dispatch = aiDispatchRepository.findById(dispatchId).orElseThrow();
            assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.FAILED);
        });

        assertThat(aiActionInboxRepository.findAll()).isEmpty();
        AI_AGENT.verify(3, com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlPathEqualTo(fetchPath())));
    }

    @Test
    void tcC6_ayniDispatchTekrarPendingeDusseBileCiftInboxKaydiOlusmaz() {
        UUID dcaseTicketId = UUID.randomUUID();
        stubAiAgentSuccess("sol-it-2", "Ikinci tur oneri", true);
        Long dispatchId = seedTicketContextDispatch(dcaseTicketId, 1, "R4");

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() ->
                assertThat(aiDispatchRepository.findById(dispatchId).orElseThrow().getStatus())
                        .isEqualTo(DispatchStatus.COMPLETED));
        assertThat(aiActionInboxRepository.findAll()).hasSize(1);
        UUID firstSourceMessageId = aiActionInboxRepository.findAll().get(0).getSourceMessageId();

        // EP tarafinda arada bir sorun olup dispatch tekrar PENDING'e dusmus gibi simule ediyoruz.
        jdbcTemplate.update("UPDATE ai_dispatch SET status='PENDING', claimed_by=NULL, claimed_at=NULL WHERE id=?",
                dispatchId);

        await().pollDelay(java.time.Duration.ofSeconds(2)).atMost(AWAIT_TIMEOUT).untilAsserted(() ->
                assertThat(aiDispatchRepository.findById(dispatchId).orElseThrow().getStatus())
                        .isEqualTo(DispatchStatus.COMPLETED));

        List<AiActionInbox> inbox = aiActionInboxRepository.findAll();
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).getSourceMessageId()).isEqualTo(firstSourceMessageId);
        // C5.3 regresyonu: AI Agent'a ikinci kez gidilmemis olmali (yalnizca ilk turdaki 1 istek).
        AI_AGENT.verify(1, com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlPathEqualTo(fetchPath())));
    }

    @Test
    void tcC10_aiStatusResultFAILUREDonerseDispatchFailedOlurInboxYazilmaz() {
        // 2026-09-15 sozlesmesi (Didar rehberi §8): HTTP 200 + statusResult=FAILURE ISLEV olarak
        // basarisizliktir — DCase'e yazilacak bir oneri yoktur.
        UUID dcaseTicketId = UUID.randomUUID();
        stubAiAgentFailure("SESSION_EXPIRED", "Oneri oturumu zaman asimina ugradi.");
        Long dispatchId = seedTicketContextDispatch(dcaseTicketId, 1, "R4");

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() ->
                assertThat(aiDispatchRepository.findById(dispatchId).orElseThrow().getStatus())
                        .isEqualTo(DispatchStatus.FAILED));

        assertThat(aiActionInboxRepository.findAll()).isEmpty();
        var process = aiProcessRepository.findAll().get(0);
        assertThat(process.getErrorCode()).isEqualTo("SESSION_EXPIRED");
        assertThat(process.getStatusResult()).isEqualTo("FAILURE");
        assertThat(process.getTransactionId()).isEqualTo("txn-err");
    }

    @Test
    void tcC11_aiInteractionaYazilanIstekGovdesindePiiMASKELENIR_amaAIyaHamGider() {
        // KVKK (rehber §6/§9): MSISDN ve musteri adi AI'a ham gitmek ZORUNDA (Oracle sorgusu icin),
        // ama kendi denetim tablomuza maskeli dusmeli.
        UUID dcaseTicketId = UUID.randomUUID();
        stubAiAgentSuccess("sol-it-3", "Oneri", true);
        Long dispatchId = seedTicketContextDispatch(dcaseTicketId, 1, "R4");

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() ->
                assertThat(aiDispatchRepository.findById(dispatchId).orElseThrow().getStatus())
                        .isEqualTo(DispatchStatus.COMPLETED));

        // (a) DB'ye maskeli yazildi
        String storedRequest = aiInteractionRepository.findAll().get(0).getRequestBody();
        assertThat(storedRequest).doesNotContain("905906020100", "TEST2506");
        assertThat(storedRequest).contains("Iade bakiyeme yansimadi");

        // (b) AI Agent'a giden GERCEK govdede ham degerler var (WireMock aldigi istegi dogruluyor)
        AI_AGENT.verify(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlPathEqualTo(fetchPath()))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("905906020100")));
    }

    @Test
    void tcC12_gonderilenIstekTEKSEMA_nestedVeZarfAlanlariIcerir() {
        UUID dcaseTicketId = UUID.randomUUID();
        stubAiAgentSuccess("sol-it-4", "Oneri", true);
        Long dispatchId = seedTicketContextDispatch(dcaseTicketId, 1, "R4");

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() ->
                assertThat(aiDispatchRepository.findById(dispatchId).orElseThrow().getStatus())
                        .isEqualTo(DispatchStatus.COMPLETED));

        var wm = com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlPathEqualTo(fetchPath()));
        AI_AGENT.verify(wm
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.schemaVersion"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.traceId"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.idempotencyKey"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock
                        .matchingJsonPath("$.ticket.category.product"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock
                        .matchingJsonPath("$.processing.iteration")));
        // Eski duz sema alanlari ARTIK GONDERILMIYOR.
        AI_AGENT.verify(0, wm.withRequestBody(
                com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.main_category")));
    }
}
