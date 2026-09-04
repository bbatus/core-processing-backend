package com.vodafone.genaiops.cpb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.vodafone.genaiops.cpb.repository.AiActionInboxRepository;
import com.vodafone.genaiops.cpb.repository.AiDispatchRepository;
import com.vodafone.genaiops.cpb.repository.AiInteractionRepository;
import com.vodafone.genaiops.cpb.repository.AiProcessRepository;
import com.vodafone.genaiops.cpb.repository.AuditLogRepository;
import com.vodafone.genaiops.cpb.repository.TicketContextRepository;
import com.vodafone.genaiops.cpb.repository.TicketRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * TC-C1..C9 için ortak Testcontainers altyapısı — gerçek PostgreSQL (EP'nin paylaşılan tablolarının
 * KÜÇÜK bir aynası, bkz. {@code ep-shared-schema-for-tests.sql}) + WireMock (AI Agent yerine).
 * "Singleton container" deseni — EP'nin AbstractIntegrationTest'iyle aynı yaklaşım.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected TicketRepository ticketRepository;
    @Autowired
    protected TicketContextRepository ticketContextRepository;
    @Autowired
    protected AiDispatchRepository aiDispatchRepository;
    @Autowired
    protected AiActionInboxRepository aiActionInboxRepository;
    @Autowired
    protected AiProcessRepository aiProcessRepository;
    @Autowired
    protected AiInteractionRepository aiInteractionRepository;
    @Autowired
    protected AuditLogRepository auditLogRepository;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected ObjectMapper objectMapper;

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("cpb_it")
            .withUsername("cpb_it")
            .withPassword("cpb_it")
            .withInitScript("ep-shared-schema-for-tests.sql");

    protected static final WireMockServer AI_AGENT = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        AI_AGENT.start();
    }

    @BeforeEach
    void resetWireMockPerTest() {
        AI_AGENT.resetAll();
    }

    @DynamicPropertySource
    static void dynamicProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.table", () -> "flyway_schema_history_cpb");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("spring.flyway.baseline-version", () -> "0");

        registry.add("app.ai.base-url", AI_AGENT::baseUrl);
        registry.add("app.dispatch.poll-interval-ms", () -> "500");
        registry.add("app.dispatch.claim-timeout-minutes", () -> "15");
    }

    /** {@code AI_AGENT.baseUrl() + /api/v1/solutions/fetch} — application.yml'deki varsayılan path. */
    protected static String fetchPath() {
        return "/api/v1/solutions/fetch";
    }

    protected static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(15);

    /** Gerçekçi bir ticket + context v1 + dispatch (R4, PENDING) seed eder, dispatch id'sini döner. */
    protected Long seedTicketContextDispatch(UUID dcaseTicketId, int version, String triggerRule) {
        UUID orgId = UUID.randomUUID();
        Long ticketId = jdbcTemplate.queryForObject("""
                INSERT INTO ticket (dcase_ticket_id, ticket_number, title, description, current_version, status,
                    assigned_group, assignee_id, assignee_name, customer_id, customer_name, category, priority,
                    organization_id, created_at, updated_at)
                VALUES (?, 79024, 'Iade bakiyeme yansimadi', 'aciklama', ?, 'PROCESSING', 'IT Operasyon',
                    gen_random_uuid(), 'srvc.vpaitst', gen_random_uuid(), 'TEST2506', 'Cuzdan', 'Orta',
                    ?, now(), now())
                RETURNING id
                """, Long.class, dcaseTicketId, version, orgId);

        String contextJson = """
                {"ticket":{"title":"Iade bakiyeme yansimadi","phoneNumber":"905906020100","priority":"Orta",
                "category":{"product":"Cuzdan","mainCategory":"Iadeler","subCategory":"Bakiye dusmedi"},
                "customer":{"fullName":"TEST2506"}},"comments":[],"processing":{"version":%d,"humanFeedback":null}}
                """.formatted(version);
        Long contextId = jdbcTemplate.queryForObject("""
                INSERT INTO ticket_context (ticket_id, version, context_json, include_attachments, created_at)
                VALUES (?, ?, ?::jsonb, false, now()) RETURNING id
                """, Long.class, ticketId, version, contextJson);

        return jdbcTemplate.queryForObject("""
                INSERT INTO ai_dispatch (ticket_id, version, context_id, trigger_rule, status, created_at)
                VALUES (?, ?, ?, ?, 'PENDING', now()) RETURNING id
                """, Long.class, ticketId, version, contextId, triggerRule);
    }

    protected void stubAiAgentSuccess(String solutionUniqueid, String solution, String status) {
        AI_AGENT.stubFor(WireMock.post(WireMock.urlPathEqualTo(fetchPath()))
                .willReturn(WireMock.okJson("""
                        {"solution_uniqueid":"%s","solution":"%s","status":"%s"}
                        """.formatted(solutionUniqueid, solution, status))));
    }
}
