package eightjbbm.keepgo.chat.service;

import eightjbbm.keepgo.chat.service.dto.ExtractSlotRequestV2;
import eightjbbm.keepgo.chat.service.dto.ExtractSlotResponseV2;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.util.client.ai.sse.AiSseClient;
import eightjbbm.keepgo.util.client.ai.sse.AiStreamResult;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AiServerExtractSlotApiService {
    private final AiSseClient sseClient;
    private final ObjectMapper mapper;

    /// `delta`의 텍스트는 도착하는 대로 `onDelta`에 전달하고, `done`의 본문을 돌려준다.
    /// `done` 없이 끝나거나 `error`를 받으면 `AiStreamException`을 던지므로 호출자는 반환된 값만 저장한다.
    public ExtractSlotResponseV2 extractSlot(String content, OutingCriteria outingCriteria, Consumer<String> onDelta) {
        AiStreamResult result =
                sseClient.stream("/v3/extract/stream", ExtractSlotRequestV2.from(content, outingCriteria), event -> {
                    if (event.name().equals("delta")) {
                        onDelta.accept(event.data().path("text").asString());
                    }
                });
        return mapper.treeToValue(result.done(), ExtractSlotResponseV2.class);
    }
}
