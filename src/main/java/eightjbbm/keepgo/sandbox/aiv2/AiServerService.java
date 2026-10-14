package eightjbbm.keepgo.sandbox.aiv2;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiServerService {

    private final RestClient restClient;

    public AnalyzeVideoResponseV2 analyzeVideoV2(List<String> urls) {
        return restClient.post()
                .uri("/v1/analyze-video")
                .body(convert(urls))
                .exchange((_, res) -> {
                    int status = res.getStatusCode().value();
                    return switch (status) {
                        case 200 -> res.bodyTo(AnalyzeVideoResponseV2.Success.class);
                        case 400, 422, 429, 500, 502, 504 -> res.bodyTo(AnalyzeVideoResponseV2.Failure.class);
                        default -> throw new RuntimeException(); // 예외 처리 구문 보완하기
                    };
                });
    }

    private AnalyzeVideoRequestV2 convert(List<String> urls) {
        var videoUrls = urls.stream()
                .map(k -> {
                    return "https://youtube.com/shorts/" + k;
                })
                .toList();
        return new AnalyzeVideoRequestV2(videoUrls);
    }
}
