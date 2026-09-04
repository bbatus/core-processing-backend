package com.vodafone.genaiops.cpb.dto;

/** Bir AI Agent HTTP cagrisinin tam sonucu — {@code ai_interaction} kaydi icin gereken her sey
 * (ham istek/yanit dahil) burada tasinir. */
public record AiCallResult(
        String requestBodyJson,
        Integer httpStatus,
        String responseBodyRaw,
        AiFetchResponse response,
        String errorMessage,
        long durationMs) {

    public boolean success() {
        return errorMessage == null && response != null;
    }
}
