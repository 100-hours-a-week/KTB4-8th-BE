package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.member.entity.OutingCollectionPrivate;
import eightjbbm.keepgo.member.repository.OutingCollectionPrivateRepository;
import eightjbbm.keepgo.recommendation.dto.*;
import eightjbbm.keepgo.util.AiServerClient;
import eightjbbm.keepgo.util.dto.RecommendCourseRequest;
import eightjbbm.keepgo.util.dto.RecommendCourseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final AiServerClient aiServerClient;
    private final OutingCollectionPrivateRepository outingCollectionPrivateRepository;

    public RequestRecommendationResult requestRecommendation(RequestRecommendationCommand command) {
        /*
        여정 코스 추천 API
        AI 서버 API 명세서를 보고 필요한 값을 갖다주기
        작업은 일단 비동기로 진행시켜야 함
        1. 사용자 위치를 이용해서 사용자가 보관한 장소/이벤트를 1차로 필터링한다
        2.
        202의 Location에 해당하는 결과 조회 API 엔드포인트 삽입
        */
        List<OutingCollectionPrivate> collection = outingCollectionPrivateRepository.findAll(); //location으로 1차 필터링

        RecommendCourseResponse recommendCourseResponse = aiServerClient.recommendCourse(RecommendCourseRequest.from(
                null,
                collection,
                command.availableTime(),
                command.category(),
                command.requestedTime(),
                command.originLat(),
                command.originLng()
        ));
        return RequestRecommendationResult.from(

        );
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
