package com.vodafone.genaiops.cpb.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.vodafone.genaiops.cpb.config.AiProperties;
import com.vodafone.genaiops.cpb.dto.AiCallResult;
import com.vodafone.genaiops.cpb.dto.AiFetchRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class AiAgentClientImplTest {

    private WireMockServer wireMock;
    private AiAgentClientImpl client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();

        AiProperties aiProperties = new AiProperties(wireMock.baseUrl(), "/api/v1/solutions/fetch",
                "/api/v1/solutions/confirm-status", null, 2000, 5, 3, 5, "GenAI Ops", false);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        RestClient restClient = RestClient.builder().baseUrl(wireMock.baseUrl()).requestFactory(factory).build();

        client = new AiAgentClientImpl(restClient, aiProperties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void ilkDenemedeBasarili_TekAttemptDoner() {
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/solutions/fetch"))
                .willReturn(okJson("""
                        {"solution_uniqueid":"s1","solution":"cozum","status":"NEEDS_APPROVAL"}
                        """)));

        List<AiCallResult> results = client.fetchWithRetries(sampleRequest());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
        assertThat(results.get(0).response().solutionUniqueid()).isEqualTo("s1");
        assertThat(results.get(0).httpStatus()).isEqualTo(200);
    }

    @Test
    void ikiKez500SonraBasarili_UcAttemptDonerSonuncuBasarili() {
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/solutions/fetch"))
                .inScenario("retry")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("second"));
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/solutions/fetch"))
                .inScenario("retry")
                .whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("third"));
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/solutions/fetch"))
                .inScenario("retry")
                .whenScenarioStateIs("third")
                .willReturn(okJson("""
                        {"solution_uniqueid":"s2","solution":"cozum2","status":"NEEDS_APPROVAL"}
                        """)));

        List<AiCallResult> results = client.fetchWithRetries(sampleRequest());

        assertThat(results).hasSize(3);
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(1).success()).isFalse();
        assertThat(results.get(2).success()).isTrue();
        assertThat(results.get(2).response().solutionUniqueid()).isEqualTo("s2");
    }

    @Test
    void herZaman500_MaxAttemptsKadarDenerHicBasariliOlmaz() {
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/solutions/fetch"))
                .willReturn(aResponse().withStatus(500)));

        List<AiCallResult> results = client.fetchWithRetries(sampleRequest());

        assertThat(results).hasSize(3);
        assertThat(results).noneMatch(AiCallResult::success);
        assertThat(results.get(2).errorMessage()).isNotNull();
    }

    @Test
    void herDenemeIcinRequestBodyJsonDoluGelir() {
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/solutions/fetch"))
                .willReturn(okJson("""
                        {"solution_uniqueid":"s3","solution":"c","status":"NEEDS_APPROVAL"}
                        """)));

        List<AiCallResult> results = client.fetchWithRetries(sampleRequest());

        assertThat(results.get(0).requestBodyJson()).contains("ticket-123");
        wireMock.verify(WireMock.postRequestedFor(urlPathEqualTo("/api/v1/solutions/fetch"))
                .withHeader("Content-Type", WireMock.containing(MediaType.APPLICATION_JSON_VALUE)));
    }

    private AiFetchRequest sampleRequest() {
        return new AiFetchRequest("ticket-123", 79024L, 1, 1, "R4", "Cuzdan", "Iadeler", "Alt kategori",
                "Baslik", "Aciklama", "9059", "Musteri", "Orta", null, List.of(), null);
    }
}
