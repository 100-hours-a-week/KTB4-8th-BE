package eightjbbm.keepgo.chat.service;

import eightjbbm.keepgo.chat.service.dto.ExtractSlotRequestV2;
import eightjbbm.keepgo.chat.service.dto.ExtractSlotResponseV2;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AiServerExtractSlotApiService {
    private final RestClient restClient;

    public AiServerExtractSlotApiService(@Qualifier("aiServerRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public ExtractSlotResponseV2 extractSlot(String content, OutingCriteria outingCriteria) {
        return restClient
                .post()
                .uri("/v1/extract")
                .body(ExtractSlotRequestV2.from(content, outingCriteria))
                .exchange((_, res) -> {
                    int status = res.getStatusCode().value();
                    return switch (status) {
                        case 200 -> res.bodyTo(ExtractSlotResponseV2.Success.class);
                        case 400, 422, 429, 500, 502, 504 -> res.bodyTo(ExtractSlotResponseV2.Failure.class);
                        default -> throw new RuntimeException();
                    };
                });
    }
}
