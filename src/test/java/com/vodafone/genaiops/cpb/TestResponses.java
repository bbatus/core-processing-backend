package com.vodafone.genaiops.cpb;

import com.vodafone.genaiops.cpb.dto.AiFetchResponse;

/**
 * Test kolayligi — {@link AiFetchResponse} 9 alanli oldugu icin testlerde her seferinde tam
 * constructor yazmak okunabilirligi bozuyordu. Didar rehberi §12'deki kabul senaryolarinin
 * karsiliklari burada isimlendirilmistir.
 */
public final class TestResponses {

    private TestResponses() {
    }

    /** §12 senaryo 1: oneri uretildi, insan onayi bekleniyor (PARTIAL). */
    public static AiFetchResponse proposal(String aiSolutionId, String solution) {
        return new AiFetchResponse(aiSolutionId, solution, "PARTIAL", true,
                "Oneri hazir, onay bekleniyor.", "txn-" + aiSolutionId, null, "2026-09-15T10:00:00Z", null);
    }

    /** §12 senaryo 2 (R5 baglaminda): ek aksiyon gerekmiyor, surec kapanabilir (SUCCESS). */
    public static AiFetchResponse noActionNeeded(String aiSolutionId, String solution) {
        return new AiFetchResponse(aiSolutionId, solution, "SUCCESS", false,
                "Ek aksiyon gerekmiyor.", "txn-" + aiSolutionId, null, "2026-09-15T10:00:00Z", null);
    }

    /** §12 senaryo 4/5/6/8: AI islemi reddetti/hata dondu (FAILURE + errorCode). */
    public static AiFetchResponse failure(String errorCode, String message) {
        return new AiFetchResponse(null, null, "FAILURE", null, message, "txn-err", errorCode,
                "2026-09-15T10:00:00Z", null);
    }

    /** {@code requiresApproval} alani HIC gelmeyen yanit — guvenli varsayilan (onay gerekir) testi icin. */
    public static AiFetchResponse withoutRequiresApproval(String aiSolutionId, String solution) {
        return new AiFetchResponse(aiSolutionId, solution, "PARTIAL", null, null, null, null, null, null);
    }
}
