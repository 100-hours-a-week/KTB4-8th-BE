package eightjbbm.keepgo.util.client.ai.analysis;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.util.client.ai.job.AiRequestException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Service
public class AiServerService {

    private final RestClient restClient;

    public AiServerService(@Qualifier("aiServerRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public AnalyzeVideoResponseV2 analyzeVideoV2(List<String> urls) {
        var request = restClient.post().uri("/v1/analyze-video").body(convert(urls));

        long startNanos = System.nanoTime();
        try {
            return request.exchange((_, res) -> {
                int status = res.getStatusCode().value();
                return switch (status) {
                    case 200 -> res.bodyTo(AnalyzeVideoResponseV2.Success.class);
                    case 400, 422, 429, 500, 502, 504 -> res.bodyTo(AnalyzeVideoResponseV2.Failure.class);
                    default -> throw new RuntimeException();
                };
            });
        } finally {
            long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            log.info("AI /v1/analyze-video 호출 소요 시간: {}ms", elapsedMillis);
        }
    }

    private AnalyzeVideoRequestV2 convert(List<String> urls) {
        var videoUrls = urls.stream()
                .map(k -> {
                    return "https://youtube.com/shorts/" + k;
                })
                .toList();
        return new AnalyzeVideoRequestV2(videoUrls);
    }

    public void embedPlaces(List<OutingGuide> newOutingGuides) {
        var request = restClient.post().uri("/v3/embed-places").body(EmbedPlaceRequest.from(newOutingGuides));

        long startNanos = System.nanoTime();
        try {
            var entity = request.retrieve().toEntity(EmbedPlaceResponse.class);
            var response = entity.getBody();
            if (response == null || !Boolean.TRUE.equals(response.success())) {
                throw new AiRequestException(
                        AiRequestException.Kind.INVALID_AI_RESPONSE,
                        entity.getStatusCode().value(),
                        null,
                        null);
            }
        } catch (RestClientResponseException e) {
            throw e;
        } catch (RestClientException e) {
            throw new AiRequestException(AiRequestException.Kind.INVALID_AI_RESPONSE, null, null, e);
        } finally {
            long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            log.info("AI /v3/embed-places 호출 소요 시간: {}ms", elapsedMillis);
        }
    }
}
