package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class YoutubeAnalyzeServiceConfig {

    @Bean
    public YoutubeAnalyzeService youtubeAnalyzeService() {
        return new NoopAnalyzeService();
    }
}
