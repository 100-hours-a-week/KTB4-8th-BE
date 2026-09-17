package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.recommendation.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendationService {
    public RequestRecommendationResult requestRecommendation(RequestRecommendationCommand command) {
        /*
        여정 코스 추천 API
        AI 서버 API 명세서를 보고 필요한 값을 갖다주기
        작업은 일단 비동기로 진행시켜야 함
        1. 사용자 위치를 이용해서 사용자가 보관한 장소/이벤트를 1차로 필터링한다
        2.
        202의 Location에 해당하는 결과 조회 API 엔드포인트 삽입
        */
        return null;
    }

    public GetRecommendationCourseInfoResult getRecommendationCourseInfo(GetRecommendationCourseInfoCommand command) {
        /*
        여정 코스 추천 결과 조회 API
        그럼 그냥 목록이랑 단건조회용을 싹 다 몰아넣자.
        */
        return null;
    }

    public StopRecommendationResult stopRecommendation(StopRecommendationCommand command) {
        /*
        여정 코스 추천 중단 API
        솔직히 지금 중단 기능을 알아서 구현하는 건 무리다.
        그냥 중단인 척 해야겠다.
        1. 진행 중인 추천 중에 해당하는 추천이 있으면 끼어들어 중단해보기
        */
        return null;
    }
}
