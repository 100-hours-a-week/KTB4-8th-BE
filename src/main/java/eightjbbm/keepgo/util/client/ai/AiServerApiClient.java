package eightjbbm.keepgo.util.client.ai;

import eightjbbm.keepgo.util.dto.GetAiVersionResponse;
import eightjbbm.keepgo.util.dto.WarmUpChatbotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class AiServerApiClient {
    @Qualifier("aiServerRestClient")
    private final RestClient aiServerRestClient;

    /// 분석용 읽기 제한(analysisTimeout)을 공유한다.
    public WarmUpChatbotResponse warmUp() {
        return aiServerRestClient.post().uri("/v3/warmup").retrieve().body(WarmUpChatbotResponse.class);
    }

    public GetAiVersionResponse getVersion() {
        return aiServerRestClient.get().uri("/v3/version").retrieve().body(GetAiVersionResponse.class);
    }
}
