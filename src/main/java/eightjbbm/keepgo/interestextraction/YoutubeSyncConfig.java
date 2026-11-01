package eightjbbm.keepgo.interestextraction;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(YoutubeSyncProperties.class)
public class YoutubeSyncConfig {

    /// 종료 시 합류 대기를 중단해 DB 폴링을 멈춘다.
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService youtubeSyncWaiters() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean("youtubeSyncTaskExecutor")
    public ThreadPoolTaskExecutor youtubeSyncTaskExecutor(YoutubeSyncProperties properties) {
        var executor = new ThreadPoolTaskExecutor();
        int poolSize = Math.max(4, properties.maxConcurrentSyncs());

        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(properties.maxConcurrentSyncs());
        executor.setThreadNamePrefix("youtube-sync-");

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

        return executor;
    }
}
