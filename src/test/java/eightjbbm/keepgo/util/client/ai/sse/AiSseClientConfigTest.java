package eightjbbm.keepgo.util.client.ai.sse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

@DisplayName("AI SSE 수신기 빈 설정")
class AiSseClientConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AiSseClientConfig.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues("keepgo.clients.ai.address=http://localhost:8000");

    @Test
    @DisplayName("기본 설정으로 컨텍스트가 AiSseClient 빈을 하나 만든다")
    void createsSingleClientBean() {
        runner.run(context -> assertThat(context).hasSingleBean(AiSseClient.class));
    }
}
