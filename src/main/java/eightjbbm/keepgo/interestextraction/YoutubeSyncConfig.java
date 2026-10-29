package eightjbbm.keepgo.interestextraction;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(YoutubeSyncProperties.class)
public class YoutubeSyncConfig {

    @Bean("youtubeSyncTaskExecutor")
    public ThreadPoolTaskExecutor youtubeSyncTaskExecutor() {
        var executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("youtube-sync-");

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

        return executor;
    }
}
