package eightjbbm.keepgo.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.util.client.ai.analysis.AiServerService;
import eightjbbm.keepgo.util.client.ai.gateway.AiCallProperties;
import eightjbbm.keepgo.util.client.ai.gateway.AiCallSlotRepository;
import eightjbbm.keepgo.util.client.ai.gateway.DefaultAiCallGateway;
import eightjbbm.keepgo.util.client.ai.job.VideoAnalysisJobClient;
import eightjbbm.keepgo.util.client.ai.job.VideoAnalysisResponse;
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
            3,
            1,
            Duration.ofSeconds(150),
            Duration.ofMillis(200),
            Duration.ofSeconds(130),
            Duration.ofSeconds(5),
            Duration.ofSeconds(30));

    private final AiCallSlotRepository slots;
    private final AiServerService aiServerService;
    private final VideoAnalysisJobClient videoAnalysisJobClient;
    private final JdbcTemplate jdbcTemplate;
    private final List<DefaultAiCallGateway> gateways = new ArrayList<>();

    AiCallGatewayIntegrationTest(
            AiCallSlotRepository slots,
            AiServerService aiServerService,
            VideoAnalysisJobClient videoAnalysisJobClient,
            JdbcTemplate jdbcTemplate) {
        this.slots = slots;
        this.aiServerService = aiServerService;
        this.videoAnalysisJobClient = videoAnalysisJobClient;
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
                calls.add(gateway.submitVideoAnalysis(i, List.of("v" + i), () -> {}));
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

        CompletableFuture<?> call = gateway().submitVideoAnalysis(1L, List.of("v0"), () -> {});

        assertThat(call).succeedsWithin(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("5xx가 나도 같은 배치를 다시 보내지 않는다")
    void doesNotRetryAnalysisOnServerError() {
        AI_STUB.failAnalysis(503, "{\"message\":\"unavailable\"}");

        CompletableFuture<?> call = gateway().submitVideoAnalysis(1L, List.of("v0"), () -> {});
        call.handle((response, error) -> null).join();

        assertThat(AI_STUB.analysisRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("대기열에서 오래 기다린 배치도 전송 후 타임아웃 안에 응답하면 성공한다")
    void waitingTimeIsNotPartOfHttpTimeout() {
        // 전체 제한은 1초다. 대기(앞 배치 0.7초 x 2)가 더 길어도 마지막 배치는 성공해야 한다.
        AI_STUB.setAnalysisDelay(Duration.ofMillis(700));
        DefaultAiCallGateway gateway = gateway(new AiCallProperties(
                1,
                1,
                Duration.ofSeconds(150),
                Duration.ofMillis(50),
                Duration.ofSeconds(1),
                Duration.ofMillis(500),
                Duration.ofSeconds(30)));

        List<CompletableFuture<VideoAnalysisResponse>> calls = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            calls.add(gateway.submitVideoAnalysis(i, List.of("v" + i), () -> {}));
        }

        assertThat(calls.getLast().join().results()).hasSize(1);
    }

    @Test
    @DisplayName("임베딩 호출은 인스턴스가 여러 개여도 동시에 1건만 보낸다")
    void sharesGlobalEmbeddingLimitAcrossInstances() {
        AI_STUB.setEmbeddingDelay(Duration.ofMillis(300));
        OutingGuide guide = mock(OutingGuide.class);
        when(guide.getId()).thenReturn(1L);

        List<CompletableFuture<?>> calls = new ArrayList<>();
        for (DefaultAiCallGateway gateway : List.of(gateway(), gateway())) {
            for (int i = 0; i < 2; i++) {
                calls.add(gateway.submitEmbedding(List.of(guide)));
            }
        }
        CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();

        assertThat(AI_STUB.embeddingRequestCount()).isEqualTo(4);
        assertThat(AI_STUB.maxConcurrentRequests()).isEqualTo(1);
    }

    private DefaultAiCallGateway gateway() {
        return gateway(PROPERTIES);
    }

    private DefaultAiCallGateway gateway(AiCallProperties properties) {
        DefaultAiCallGateway gateway =
                new DefaultAiCallGateway(aiServerService, videoAnalysisJobClient, slots, properties);
        gateway.start();
        gateways.add(gateway);
        return gateway;
    }
}
