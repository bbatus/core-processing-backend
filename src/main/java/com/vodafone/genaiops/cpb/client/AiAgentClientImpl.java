package com.vodafone.genaiops.cpb.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vodafone.genaiops.cpb.config.AiProperties;
import com.vodafone.genaiops.cpb.dto.AiCallResult;
import com.vodafone.genaiops.cpb.dto.AiFetchRequest;
import com.vodafone.genaiops.cpb.dto.AiFetchResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Manuel retry dongusu (Resilience4j'nin bildirim-tabanli @Retry'si yerine BILEREK) — her denemenin
 * ham istek/yanitini {@link AiCallResult} olarak ayri ayri doner, boylece cagiran taraf HER denemeyi
 * (basarisiz olanlar dahil) {@code ai_interaction}'a "AI ne yapmak istedi, ne dondu" seklinde
 * kaydedebilir (kullanici karari, bkz. TASARIM_PLANI §8, 2026-09-02).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAgentClientImpl implements AiAgentClient {

    private final RestClient aiAgentRestClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @Override
    public List<AiCallResult> fetchWithRetries(AiFetchRequest request) {
        List<AiCallResult> attempts = new ArrayList<>();
        String requestJson = writeJson(request);

        for (int attempt = 1; attempt <= aiProperties.maxAttempts(); attempt++) {
            long start = System.currentTimeMillis();
            try {
                RestClient.ResponseSpec responseSpec = aiAgentRestClient.post()
                        .uri(aiProperties.fetchPath())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve();

                String rawBody = responseSpec.toEntity(String.class).getBody();
                AiFetchResponse parsed = objectMapper.readValue(rawBody, AiFetchResponse.class);
                long duration = System.currentTimeMillis() - start;

                AiCallResult result = new AiCallResult(requestJson, 200, rawBody, parsed, null, duration);
                attempts.add(result);
                return attempts;
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - start;
                log.warn("AI Agent cagrisi basarisiz (deneme {}/{}): {}", attempt, aiProperties.maxAttempts(),
                        e.getMessage());
                attempts.add(new AiCallResult(requestJson, extractStatus(e), null, null, e.getMessage(), duration));
                if (attempt == aiProperties.maxAttempts()) {
                    return attempts;
                }
                sleepBeforeRetry(attempt);
            }
        }
        return attempts;
    }

    private Integer extractStatus(Exception e) {
        if (e instanceof org.springframework.web.client.HttpStatusCodeException httpEx) {
            return httpEx.getStatusCode().value();
        }
        return null;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(1000L * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String writeJson(AiFetchRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            return "{}";
        }
    }
}
