package eightjbbm.keepgo.util.client.ai.gateway;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.util.client.ai.analysis.AnalyzeVideoResponseV2;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/// AI 서버 호출의 유일한 입구. 호출자는 AI 용량을 모른 채 배치를 제출하고 결과 Future만 받는다.
public interface AiCallGateway {

    /// 동기화 하나의 분석 배치를 제출한다. `beforeSend`는 슬롯을 얻은 뒤 실제 전송 직전에 실행된다.
    CompletableFuture<AnalyzeVideoResponseV2> submitAnalysis(long syncId, List<String> videoIds, Runnable beforeSend);

    /// 커밋된 가이드의 임베딩 요청을 제출한다.
    CompletableFuture<Void> submitEmbedding(List<OutingGuide> guides);
}
