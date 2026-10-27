package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.recommendation.entity.OutingCourse;
import eightjbbm.keepgo.recommendation.entity.OutingStop;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.repository.OutingCourseRepository;
import eightjbbm.keepgo.recommendation.repository.OutingStopRepository;
import eightjbbm.keepgo.recommendation.service.dto.RecommendCoursesResponseV2;
import eightjbbm.keepgo.recommendation.service.dto.RecommendOutingCourseResultV2;
import eightjbbm.keepgo.sandbox.OutingGuideRepository;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RecommendationResultMapper {

    private final OutingCourseRepository outingCourseRepository;
    private final OutingStopRepository outingStopRepository;
    private final OutingGuideRepository outingGuideRepository;
    private static final DateTimeFormatter SCHEDULE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.of("Asia/Seoul"));

    @Transactional
    public RecommendOutingCourseResultV2 map(
            OutingCriteria outingCriteria,
            RecommendCoursesResponseV2.SuccessData data
    ) {
        int matchScore = data.matchScore() == null ? 85 : data.matchScore();
        List<OutingCourse> outingCourses = data.courses()
                .stream()
                .map(
                        course -> {
                            var outingCourse = new OutingCourse(
                                course.title(),
                                course.totalDurationMinutes(),
                                matchScore,
                                outingCriteria.getRequestedLocationName(),
                                    null);
                            var savedCourse = outingCourseRepository.save(outingCourse);
                            List<OutingStop> stops = course.places().stream()
                                    .map(place -> new OutingStop(
                                            savedCourse,
                                            place.travelMinutes(),
                                            course.places().indexOf(place),
                                            outingGuideRepository.getReferenceById(Long.valueOf(place.placeId()))))
                                    .collect(Collectors.toCollection(ArrayList::new));
                            outingStopRepository.saveAll(stops);
                            outingCourse.setStops(stops);
                            return outingCourse;
                        }
                )
                .toList();

        return new RecommendOutingCourseResultV2(
                new RecommendOutingCourseResultV2.OutingCourseMetadata(
                        data.courses().size(),
                        outingCriteria.getRequestedLocationName(),
                        outingCriteria.getRequestedDatetime().atZone(ZoneId.of("Asia/Seoul")).toLocalDateTime().toString()
                ),
                outingCourses.stream()
                        .map(outingCourse -> new RecommendOutingCourseResultV2.OutingCourseSnippet(
                                outingCourse.getName(),
                                String.valueOf(outingCourse.getRecommendationScore()),
                                outingCourse.getStops().size(),
                                outingCourse.getDurationHours()
                        ))
                        .toList(),
                outingCourses.stream()
                        .map(outingCourse -> new RecommendOutingCourseResultV2.OutingCourseDetail(
                                outingCourse.getId(),
                                outingCourse.getName(),
                                String.valueOf(outingCourse.getRecommendationScore()),
                                outingCourse.getDurationHours(),
                                outingCourse.getStops().stream().map(
                                        outingStop -> new RecommendOutingCourseResultV2.OutingCourseStop(
                                                outingStop.getId().sequence(),
                                                outingStop.getOutingGuide().getName(),
                                                outingStop.getOutingGuide().getCategory(),
                                                outingStop.getTravelMinutes(),
                                                outingStop.getOutingGuide().getCoordinate().lat(),
                                                outingStop.getOutingGuide().getCoordinate().lng()
                                        )
                                ).toList()
                        ))
                        .toList()
        );
    }

    private int travelMinutes(RecommendCoursesResponseV2.Course course) {
        return course.places().stream()
                .mapToInt(place -> {
                    if (place.travelMinutes() == null
                            || place.travelMinutes() < 0) {
                        throw new ServiceUnavailableException();
                    }

                    return place.travelMinutes();
                })
                .sum();
    }
}
