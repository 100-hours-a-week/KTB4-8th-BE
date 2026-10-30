package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.recommendation.entity.OutingCourse;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.entity.OutingStop;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.repository.OutingCourseRepository;
import eightjbbm.keepgo.recommendation.repository.OutingGuideRepository;
import eightjbbm.keepgo.recommendation.repository.OutingStopRepository;
import eightjbbm.keepgo.recommendation.service.dto.RecommendCoursesResponseV2;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationCourseService {

    private static final int DEFAULT_MATCH_SCORE = 85;

    private final OutingCourseRepository outingCourseRepository;
    private final OutingStopRepository outingStopRepository;
    private final OutingGuideRepository outingGuideRepository;

    /// 추천 응답의 코스와 스톱을 저장한다.
    /// 가이드를 트랜잭션 안에서 미리 읽어 두므로, 반환된 엔티티를 트랜잭션 밖에서 DTO로 변환할 수 있다.
    @Transactional
    public List<OutingCourse> saveCourses(OutingCriteria criteria, RecommendCoursesResponseV2.SuccessData data) {
        int matchScore = data.matchScore() == null ? DEFAULT_MATCH_SCORE : data.matchScore();
        Map<Long, OutingGuide> guidesById = findGuides(data);

        return data.courses().stream()
                .map(course -> saveCourse(criteria, course, matchScore, guidesById))
                .toList();
    }

    private Map<Long, OutingGuide> findGuides(RecommendCoursesResponseV2.SuccessData data) {
        List<Long> guideIds = data.courses().stream()
                .flatMap(course -> course.places().stream())
                .map(place -> Long.valueOf(place.placeId()))
                .distinct()
                .toList();

        return outingGuideRepository.findAllById(guideIds).stream()
                .collect(Collectors.toMap(OutingGuide::getId, Function.identity()));
    }

    private OutingCourse saveCourse(
            OutingCriteria criteria,
            RecommendCoursesResponseV2.Course course,
            int matchScore,
            Map<Long, OutingGuide> guidesById) {
        var outingCourse = new OutingCourse(
                course.title(), course.totalDurationMinutes(), matchScore, criteria.getRequestedLocationName(), null);
        var savedCourse = outingCourseRepository.save(outingCourse);

        List<OutingStop> stops = IntStream.range(0, course.places().size())
                .mapToObj(index -> {
                    var place = course.places().get(index);
                    var guide = guidesById.get(Long.valueOf(place.placeId()));
                    if (guide == null || place.travelMinutes() == null) {
                        throw new ServiceUnavailableException();
                    }
                    return new OutingStop(savedCourse, place.travelMinutes(), index + 1, guide);
                })
                .toList();

        outingStopRepository.saveAll(stops);
        savedCourse.replaceStops(stops);

        return savedCourse;
    }
}
