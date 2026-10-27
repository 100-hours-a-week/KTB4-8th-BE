package eightjbbm.keepgo.interestextraction.service.youtubeanalyzev3;

import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisManager;
import eightjbbm.keepgo.sandbox.OutingGuideRepository;
import eightjbbm.keepgo.sandbox.aiv2.AiServerService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class YoutubeAnalyzeServiceConfig {

    @Bean
    public YoutubeVideoAnalyzer youtubeVideoAnalyzer(
            AiServerService aiServerApiService,
            OutingGuideRepository outingGuideRepository,
            VideoAnalysisManager videoAnalysisManager
    ) {
        return new DefaultYoutubeVideoAnalyzer(
                aiServerApiService, outingGuideRepository, videoAnalysisManager);
    }
}
