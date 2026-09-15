package com.vodafone.genaiops.cpb.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.vodafone.genaiops.cpb.config.AiProperties;
import com.vodafone.genaiops.cpb.dto.AiCallResult;
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
                        {"aiSolutionId":"s1","solution":"cozum","statusResult":"PARTIAL","requiresApproval":true}
                        """)));

        List<AiCallResult> results = client.fetchWithRetries(sampleRequest());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
        assertThat(results.get(0).response().aiSolutionId()).isEqualTo("s1");
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
                        {"aiSolutionId":"s2","solution":"cozum2","statusResult":"PARTIAL","requiresApproval":true}
                        """)));

        List<AiCallResult> results = client.fetchWithRetries(sampleRequest());

        assertThat(results).hasSize(3);
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(1).success()).isFalse();
        assertThat(results.get(2).success()).isTrue();
        assertThat(results.get(2).response().aiSolutionId()).isEqualTo("s2");
    }

    @Test
    void herZaman500_MaxAttemptsKadarDenerHicBasariliOlmaz() {
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/solutions/fetch"))
                .willReturn(aResponse().withStatus(500)));

        List<AiCallResult> results = client.fetchWithRetries(sampleRequest());

        assertThat(results).hasSize(3).noneMatch(AiCallResult::success);
        assertThat(results.get(2).errorMessage()).isNotNull();
    }

    @Test
    void herDenemeIcinRequestBodyJsonDoluGelir() {
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/solutions/fetch"))
                .willReturn(okJson("""
                        {"aiSolutionId":"s3","solution":"c","statusResult":"PARTIAL","requiresApproval":true}
                        """)));

        List<AiCallResult> results = client.fetchWithRetries(sampleRequest());

        assertThat(results.get(0).requestBodyJson()).contains("ticket-123");
        wireMock.verify(WireMock.postRequestedFor(urlPathEqualTo("/api/v1/solutions/fetch"))
                .withHeader("Content-Type", WireMock.containing(MediaType.APPLICATION_JSON_VALUE)));
    }

    /** 2026-09-15 tek-sema sozlesmesi: istek artik EP'nin nested context_json'i + CPB zarfi. */
    private JsonNode sampleRequest() {
        try {
            return new ObjectMapper().readTree("""
                    {"schemaVersion":1,"triggerRule":"R4","traceId":"trace-1",
                     "idempotencyKey":"ticket-123:1:1",
                     "ticket":{"dcaseTicketId":"ticket-123","title":"Baslik","phoneNumber":"905551112233"},
                     "processing":{"version":1,"iteration":1,"aiSolutionId":null}}
                    """);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
