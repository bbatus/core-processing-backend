package com.vodafone.genaiops.cpb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** AI Agent → CPB yaniti. Bilinmeyen alanlar yok sayilir (AI ekibi ileride alan eklerse kirilmasin). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiFetchResponse(
        @JsonProperty("solution_uniqueid") String solutionUniqueid,
        String solution,
        String status) {
}
