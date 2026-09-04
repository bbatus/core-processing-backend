package com.vodafone.genaiops.cpb.config;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AiAgentClientConfigTest {

    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/ping"))
                .willReturn(okJson("{}")));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void apiKeyDoluysaAuthorizationHeaderiEklenir() {
        AiProperties props = new AiProperties(wireMock.baseUrl(), "/fetch", "/confirm", "gizli-anahtar",
                2000, 5, 3, 5, "GenAI Ops", false);
        RestClient client = new AiAgentClientConfig(props).aiAgentRestClient();

        client.get().uri("/ping").retrieve().toBodilessEntity();

        wireMock.verify(getRequestedFor(urlPathEqualTo("/ping"))
                .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.equalTo("Bearer gizli-anahtar")));
    }

    @Test
    void apiKeyBosSeAuthorizationHeaderiEklenmez() {
        AiProperties props = new AiProperties(wireMock.baseUrl(), "/fetch", "/confirm", "",
                2000, 5, 3, 5, "GenAI Ops", false);
        RestClient client = new AiAgentClientConfig(props).aiAgentRestClient();

        client.get().uri("/ping").retrieve().toBodilessEntity();

        List<com.github.tomakehurst.wiremock.stubbing.ServeEvent> events = wireMock.getAllServeEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getRequest().containsHeader("Authorization")).isFalse();
    }

    @Test
    void apiKeyNullIseAuthorizationHeaderiEklenmez() {
        AiProperties props = new AiProperties(wireMock.baseUrl(), "/fetch", "/confirm", null,
                2000, 5, 3, 5, "GenAI Ops", false);
        RestClient client = new AiAgentClientConfig(props).aiAgentRestClient();

        client.get().uri("/ping").retrieve().toBodilessEntity();

        List<com.github.tomakehurst.wiremock.stubbing.ServeEvent> events = wireMock.getAllServeEvents();
        assertThat(events.get(0).getRequest().containsHeader("Authorization")).isFalse();
    }
}
