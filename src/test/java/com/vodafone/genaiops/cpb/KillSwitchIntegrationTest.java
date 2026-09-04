package com.vodafone.genaiops.cpb;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.vodafone.genaiops.cpb.entity.AiDispatch;
import com.vodafone.genaiops.cpb.enums.DispatchStatus;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** TC-C7 — {@code FLOW_AI_CALL_ENABLED=false} iken AI Agent'a hiç gidilmemeli, dispatch PENDING'e
 * geri dönmeli. Ayrı bir Spring context (farklı property seti) ile çalışır, aynı Postgres/WireMock
 * container'larını (singleton, AbstractIntegrationTest'ten) paylaşır. */
class KillSwitchIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void killSwitchProps(DynamicPropertyRegistry registry) {
        registry.add("flow.ai-call.enabled", () -> "false");
    }

    @Test
    void tcC7_aiCallKapaliysaDispatchPendingeGeriDonerAiHicCagrilmaz() {
        UUID dcaseTicketId = UUID.randomUUID();
        Long dispatchId = seedTicketContextDispatch(dcaseTicketId, 1, "R4");

        // Poller birkac kez calissin (500ms interval) - her seferinde claim edip geri birakmali.
        await().pollDelay(Duration.ofSeconds(3)).atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            AiDispatch dispatch = aiDispatchRepository.findById(dispatchId).orElseThrow();
            assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.PENDING);
        });

        assertThat(aiActionInboxRepository.findAll()).isEmpty();
        AI_AGENT.verify(0, postRequestedFor(urlPathEqualTo(fetchPath())));
    }
}
