package eightjbbm.keepgo.embedding;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableConfigurationProperties(EmbeddingOutboxProperties.class)
public class EmbeddingOutboxConfig {

    /// 임베딩 호출은 최대 100건을 순차로 처리하며 오래 걸릴 수 있으므로,
    /// heartbeat·sweeper가 공유하는 기본 스케줄러와 분리한다.
    @Bean("embeddingOutboxTaskScheduler")
    public TaskScheduler embeddingOutboxTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("embedding-outbox-");
        return scheduler;
    }
}
