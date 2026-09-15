package com.vodafone.genaiops.cpb.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vodafone.genaiops.cpb.entity.AiDispatch;
import com.vodafone.genaiops.cpb.entity.Ticket;
import com.vodafone.genaiops.cpb.entity.TicketContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * EP'nin urettigi {@code ticket_context.context_json}'ini AI Agent istegine cevirir.
 *
 * <p><b>⚠️ 2026-09-15'te koklu olarak degisti — "tek sema" duzeltmesi.</b> Onceki surum, ayni istekte
 * HEM duz (snake_case) alanlari ({@code ticket_id}, {@code main_category}, {@code msisdn} …) HEM de
 * tum {@code context_json} blogunu gonderiyordu. Didar ekibi bunu hakli olarak "iki farkli sema
 * paralel yasiyor, validation yapilamiyor" diye raporladi (entegrasyon rehberi §0 bulgu #3, e-posta
 * madde 2). Artik <b>tek sema</b> gonderiliyor: EP'nin {@code context_json}'i <em>birebir</em>
 * (nested, {@code schemaVersion}'li — EP zaten boyle uretiyor, bkz. {@code TicketContextPayload}) +
 * yalnizca CPB'nin bilebilecegi zarf alanlari.</p>
 *
 * <p><b>CPB'nin enjekte ettigi alanlar (EP'nin semasina DOKUNULMAZ):</b></p>
 * <ul>
 *   <li>{@code traceId} — uctan uca log korelasyonu (rehber §4.1; CPB'nin MDC correlationId'si).</li>
 *   <li>{@code idempotencyKey} — {@code <dcaseTicketId>:<version>:<iteration>} (rehber e-posta madde
 *       6; bizim {@code ai_process} tablosundaki tekil anahtarla birebir ayni uclu).</li>
 *   <li>{@code processing.iteration} — kacinci AI turu.</li>
 *   <li>{@code processing.aiSolutionId} — R4'te {@code null}, R5/R6'da onceki turun AI oneri
 *       kimligi. <b>Bu alan bilerek EP'nin semasina EKLENMEDI</b> (Didar "EP semasina eklensin"
 *       demisti): {@code aiSolutionId} AI↔CPB arasinda bir oturum korelasyon anahtaridir, EP onu ne
 *       uretir ne kullanir; CPB zaten {@code ai_process.solution_uniqueid}'de saklar. EP'nin
 *       {@code context_json}'i "ticket'in o anki hali"dir — AI oturum kimligi oraya girerse EP/CPB
 *       kapsam siniri bulanir (bkz. EP CLAUDE.md §2). Didar alani istedigi yolda ({@code
 *       processing.aiSolutionId}) gorur, EP semasi temiz kalir.</li>
 * </ul>
 *
 * <p>{@code triggerRule} EP'nin context'inde de var, ancak burada <b>dispatch'inki</b> yazilir —
 * bu cagriyi tetikleyen operatif kural odur (ikisi normalde ayni; farklilarsa dispatch otoriterdir).</p>
 */
@RequiredArgsConstructor
@Component
public class ContextRequestMapper {

    private static final int FALLBACK_SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    /**
     * @param previousAiSolutionId R5/R6 turunda onceki turun {@code aiSolutionId}'si; R4'te (ilk tur)
     *                             {@code null}. AI Agent bu alanla hangi oneriden bahsedildigini
     *                             dogrular (rehber §3 adim 7, "eslesme kontrolu").
     * @param traceId              uctan uca izleme kimligi (CPB'nin MDC correlationId'si).
     */
    public JsonNode toRequest(Ticket ticket, TicketContext ticketContext, AiDispatch dispatch, int iteration,
            String previousAiSolutionId, String traceId) {
        ObjectNode root = readContextJson(ticketContext);

        // EP uretmisse dokunma; uretmemisse (savunma) varsayilani koy.
        if (!root.hasNonNull("schemaVersion")) {
            root.put("schemaVersion", FALLBACK_SCHEMA_VERSION);
        }
        if (dispatch.getTriggerRule() != null) {
            root.put("triggerRule", dispatch.getTriggerRule().name());
        }
        root.put("traceId", traceId);
        root.put("idempotencyKey", idempotencyKey(ticket, dispatch, iteration));

        ObjectNode processing = root.withObject("/processing");
        processing.put("iteration", iteration);
        if (previousAiSolutionId == null) {
            processing.putNull("aiSolutionId");
        } else {
            processing.put("aiSolutionId", previousAiSolutionId);
        }
        return root;
    }

    /**
     * Didar'in onerdigi uclu (e-posta madde 6): {@code ticket_id + version + iteration}. Ayni uclu
     * bizim {@code ai_process} tablosundaki tekillik kisitiyla ortusur, yani AI tarafinda duplicate
     * tespiti bizim tarafimizdaki tekrar-isleme korumasiyla ayni sinirlari cizer.
     */
    private String idempotencyKey(Ticket ticket, AiDispatch dispatch, int iteration) {
        return ticket.getDcaseTicketId() + ":" + dispatch.getVersion() + ":" + iteration;
    }

    private ObjectNode readContextJson(TicketContext ticketContext) {
        String json = ticketContext == null ? null : ticketContext.getContextJson();
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode parsed = objectMapper.readTree(json);
            return parsed instanceof ObjectNode objectNode ? objectNode : objectMapper.createObjectNode();
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }
}
