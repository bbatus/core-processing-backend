package com.vodafone.genaiops.cpb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * CPB → AI Agent istegi (POST /api/v1/solutions/fetch). Alan adlari CPB_TASARIM_VE_GELISTIRME_PLANI
 * §6.1'de tanimlanan teklif sozlesmesidir — AI ekibiyle netlesince degisebilir (yalnizca bu sinif
 * ve {@link com.vodafone.genaiops.cpb.mapper.ContextRequestMapper} etkilenir).
 */
public record AiFetchRequest(
        @JsonProperty("ticket_id") String ticketId,
        @JsonProperty("ticket_number") Long ticketNumber,
        int version,
        int iteration,
        @JsonProperty("trigger_rule") String triggerRule,
        String product,
        @JsonProperty("main_category") String mainCategory,
        @JsonProperty("sub_category") String subCategory,
        String title,
        @JsonProperty("problem_description") String problemDescription,
        String msisdn,
        @JsonProperty("customer_name") String customerName,
        String priority,
        @JsonProperty("human_feedback") String humanFeedback,
        List<JsonNode> comments,
        @JsonProperty("context_json") JsonNode contextJson) {
}
