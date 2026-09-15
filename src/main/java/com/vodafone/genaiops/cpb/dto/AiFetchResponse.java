package com.vodafone.genaiops.cpb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * AI Agent → CPB yaniti — Didar ekibinin entegrasyon rehberi (GENAIOPS-EPCBP_ENTEGRASYON_REHBERI_
 * REVIZE_2026-09-09.md §7) ile hizalanmis sozlesme (2026-09-15). Bilinmeyen alanlar yok sayilir.
 *
 * <p><b>Bilerek modellemediğimiz alan — {@code dcaseUpdatePayload}:</b> Didar'in dokumani §7'de bu
 * alani AI Agent'in dondurdugu bir alan olarak listeliyor, ancak ayni ekibin e-postasindaki 3.
 * gereksinim ("DCase'e yazilacak PATCH govdesini yalnizca CPB uretmelidir; AI Agent yalnizca cozum
 * metni + durum bilgisi doner") bunun tersini soyluyor — doküman kendi icinde celisiyor. Biz
 * e-postadaki maddeyi esas aliyoruz: TMF621 govdesini {@link
 * com.vodafone.genaiops.cpb.service.ActionInboxWriter} uretir. AI Agent'in DCase'in not karakter
 * limitini, assignee UUID'lerini ya da TMF621 seklini bilmesine gerek yoktur. AI bu alani yine de
 * gonderirse {@code ignoreUnknown} sayesinde sessizce yok sayilir.</p>
 *
 * @param aiSolutionId AI'in urettigi onerinin benzersiz kimligi (Maximo'daki {@code
 *                     solution_uniqueid} karsiligi). R5 turunda CPB bunu geri gonderir.
 * @param solution     Oneri metni — DCase yorumuna yazilacak asil icerik.
 * @param statusResult {@code SUCCESS} | {@code PARTIAL} | {@code FAILURE} (islemin teknik sonucu).
 * @param requiresApproval {@code true} → insan onayi gerekir. {@code null} gelirse GUVENLI VARSAYILAN
 *                     {@code true}'dur ({@link #needsApproval()}) — Faz 2'de hicbir sey insan onayi
 *                     olmadan kapanmamalidir.
 * @param message      En fazla 200 karakter, kisa ozet (log/denetim icin).
 * @param transactionId AI tarafindaki denetim kimligi.
 * @param errorCode    Yalnizca {@code statusResult=FAILURE} iken dolu (ornek: {@code AGENT_TIMEOUT},
 *                     {@code SOLUTION_ID_MISMATCH}, {@code VALIDATION_ERROR}).
 * @param syncTime     AI yanitinin uretildigi zaman (ISO-8601, UTC) — opak string olarak saklanir.
 * @param dcaseTicketId Yanitın hangi ticket'a ait oldugu (istekteki degerle ayni olmali).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiFetchResponse(
        String aiSolutionId,
        String solution,
        String statusResult,
        Boolean requiresApproval,
        String message,
        String transactionId,
        String errorCode,
        String syncTime,
        String dcaseTicketId) {

    private static final String FAILURE = "FAILURE";

    /** AI islemi basarisiz bildirdiyse {@code true} — HTTP 200 donmus olsa bile hata sayilir. */
    public boolean isFailure() {
        return FAILURE.equalsIgnoreCase(statusResult);
    }

    /**
     * Insan onayi gerekiyor mu — {@code requiresApproval} bos gelirse GUVENLI TARAFA duser
     * ({@code true}). Faz 2'de AI hicbir aksiyonu kendi basina uygulamaz; belirsizlikte insana
     * gitmek, yanlislikla kapatmaktan her zaman daha iyidir.
     */
    public boolean needsApproval() {
        return requiresApproval == null || requiresApproval;
    }

    /** Hata durumunda denetim/log icin tek satirlik ozet. */
    public String failureSummary() {
        return (errorCode == null ? "AI_FAILURE" : errorCode) + (message == null ? "" : ": " + message);
    }
}
