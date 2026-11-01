package eightjbbm.keepgo.util.client.ai.gateway;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.util.client.ai.analysis.AnalyzeVideoResponseV2;
import eightjbbm.keepgo.util.client.ai.job.VideoAnalysisResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

/// AI 서버 호출의 유일한 입구. 호출자는 AI 용량을 모른 채 배치를 제출하고 결과 Future만 받는다.
public interface AiCallGateway {

    /// 동기화 하나의 분석 배치를 제출한다. `beforeSend`는 슬롯을 얻은 뒤 실제 전송 직전에 실행된다.
    CompletableFuture<AnalyzeVideoResponseV2> submitAnalysis(long syncId, List<String> videoIds, Runnable beforeSend);

    /// 동기화 하나의 분석 배치를 복수 가이드 분석 계약으로 제출한다.
    ///
    /// `beforeSend`는 슬롯을 얻은 뒤 전송 직전에 실행되며, 요청한 영상 중 실제로 보낼 영상만 돌려준다.
    /// 보낼 영상이 없으면 AI 서버를 호출하지 않고 결과 없는 응답으로 끝낸다.
    CompletableFuture<VideoAnalysisResponse> submitVideoAnalysis(
            long syncId, List<String> videoIds, UnaryOperator<List<String>> beforeSend);

    /// 커밋된 가이드의 임베딩 요청을 제출한다.
    CompletableFuture<Void> submitEmbedding(List<OutingGuide> guides);
}
