package eightjbbm.keepgo.interestextraction.service;

import eightjbbm.keepgo.interestextraction.service.youtubeanalyzev3.InterestExtractionServiceV3;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingWishlist;
import eightjbbm.keepgo.sandbox.OutingGuideRepository;
import eightjbbm.keepgo.sandbox.aiv2.AiServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class InterestExtractionServiceConfig {

    private final YoutubeApiServiceV2 youtubeApiService;
    private final AiServerService aiServerApiService;
    private final OutingGuideRepository outingGuideRepository;
    private final MemberRepository memberRepository;
    private final OutingWishlist outingWishlist;

    @Bean
    public InterestExtractionService interestExtractionService(
            YoutubeApiServiceV2 youtubeApiService,
            AiServerService aiServerApiService,
            OutingGuideRepository outingGuideRepository,
            MemberRepository memberRepository,
            OutingWishlist outingWishlist
    ) {
        return new InterestExtractionServiceV3(
                youtubeApiService, aiServerApiService, outingGuideRepository, memberRepository, outingWishlist);
    }
}
