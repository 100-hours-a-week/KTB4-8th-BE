package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.service.dto.RecommendCoursesRequestV2;
import eightjbbm.keepgo.recommendation.service.dto.RecommendCoursesResponseV2;
import eightjbbm.keepgo.util.client.ai.sse.AiSseClient;
import eightjbbm.keepgo.util.client.ai.sse.AiStreamEvent;
import eightjbbm.keepgo.util.client.ai.sse.AiStreamHandle;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AiServerRecommendApiService {

    private final AiSseClient aiSseClient;
    private final ObjectMapper mapper;

    /// 스트림을 `done`까지 읽고 그 본문을 돌려준다. 업무 결과는 호출자가 `message`로 판정한다.
    public RecommendCoursesResponseV2.Done recommendCourses(
            OutingCriteria outingCriteria,
            List<OutingGuide> outingGuides,
            AiStreamHandle handle,
            Consumer<AiStreamEvent> onEvent) {
        var result = aiSseClient.stream(
                "/v3/recommend-course/stream",
                RecommendCoursesRequestV2.from(outingCriteria, outingGuides),
                onEvent,
                handle);
        return mapper.treeToValue(result.done(), RecommendCoursesResponseV2.Done.class);
    }
}
