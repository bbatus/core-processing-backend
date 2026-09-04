package com.vodafone.genaiops.cpb.client;

import com.vodafone.genaiops.cpb.dto.AiCallResult;
import com.vodafone.genaiops.cpb.dto.AiFetchRequest;
import java.util.List;

/** AI Agent'a (Didar ekibi) yapilan REST cagrilarinin soyutlamasi. LLM'i DOGRUDAN cagirmaz — bu
 * arayuz yalnizca AI Agent'in kendi HTTP API'sini konusur (bkz. CPB_TASARIM_VE_GELISTIRME_PLANI §6). */
public interface AiAgentClient {

    /**
     * {@code POST /api/v1/solutions/fetch} — {@code app.ai.max-attempts} kadar dener (5xx/timeout/
     * baglanti hatasinda), her denemeyi ({@code attempt_no} sirasiyla) ayri bir {@link AiCallResult}
     * olarak doner ki cagiran taraf her denemeyi {@code ai_interaction}'a kaydedebilsin. Listenin SON
     * elemani nihai sonuctur.
     */
    List<AiCallResult> fetchWithRetries(AiFetchRequest request);
}
