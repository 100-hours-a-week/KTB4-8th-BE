package eightjbbm.keepgo.integration;

import static org.assertj.core.api.Assertions.assertThat;

import eightjbbm.keepgo.util.client.ai.analysis.AiServerService;
import eightjbbm.keepgo.util.client.ai.gateway.AiCallProperties;
import eightjbbm.keepgo.util.client.ai.gateway.AiCallSlotRepository;
import eightjbbm.keepgo.util.client.ai.gateway.DefaultAiCallGateway;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

/// 같은 MySQL 슬롯 테이블을 쓰는 게이트웨이 두 개로 인스턴스 간 전역 상한과 임대 만료 회수를 검증한다.
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("AI 호출 게이트웨이 MySQL 통합 테스트")
class AiCallGatewayIntegrationTest extends MySqlIntegrationTestSupport {

    private static final AiCallProperties PROPERTIES = new AiCallProperties(
            3, 1, Duration.ofSeconds(150), Duration.ofMillis(200), Duration.ofSeconds(130), Duration.ofSeconds(5));

    private final AiCallSlotRepository slots;
    private final AiServerService aiServerService;
    private final JdbcTemplate jdbcTemplate;
    private final List<DefaultAiCallGateway> gateways = new ArrayList<>();

    AiCallGatewayIntegrationTest(
            AiCallSlotRepository slots, AiServerService aiServerService, JdbcTemplate jdbcTemplate) {
        this.slots = slots;
        this.aiServerService = aiServerService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void reset() {
        AI_STUB.reset();
        jdbcTemplate.update("UPDATE ai_call_slot SET holder = null, leased_until = null");
    }

    @AfterEach
    void shutdown() {
        gateways.forEach(DefaultAiCallGateway::shutdown);
        gateways.clear();
    }

    @Test
    @DisplayName("서로 다른 인스턴스의 게이트웨이도 분석 동시 호출을 3건으로 제한한다")
    void sharesGlobalAnalysisLimitAcrossInstances() {
        AI_STUB.setAnalysisDelay(Duration.ofMillis(300));
        DefaultAiCallGateway first = gateway();
        DefaultAiCallGateway second = gateway();

        List<CompletableFuture<?>> calls = new ArrayList<>();
        for (DefaultAiCallGateway gateway : List.of(first, second)) {
            for (int i = 0; i < 3; i++) {
                calls.add(gateway.submitAnalysis(i, List.of("v" + i), () -> {}));
            }
        }

        CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();

        assertThat(AI_STUB.maxConcurrentRequests()).isEqualTo(3);
    }

    @Test
    @DisplayName("반납 없이 죽은 인스턴스의 슬롯도 임대가 만료되면 다시 쓴다")
    void reclaimsExpiredLease() {
        jdbcTemplate.update("""
                UPDATE ai_call_slot
                   SET holder = 'dead-instance',
                       leased_until = TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6))
                 WHERE slot_type = 'ANALYSIS'
                """);

        CompletableFuture<?> call = gateway().submitAnalysis(1L, List.of("v0"), () -> {});

        assertThat(call).succeedsWithin(Duration.ofSeconds(5));
    }

    private DefaultAiCallGateway gateway() {
        DefaultAiCallGateway gateway = new DefaultAiCallGateway(aiServerService, slots, PROPERTIES);
        gateways.add(gateway);
        return gateway;
    }
}
