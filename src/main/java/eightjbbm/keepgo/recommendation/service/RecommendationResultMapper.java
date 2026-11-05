package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.recommendation.entity.OutingCourse;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.service.dto.RecommendCoursesResponseV2;
import eightjbbm.keepgo.recommendation.service.dto.RecommendOutingCourseResultV2;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RecommendationResultMapper {

    private static final DateTimeFormatter SCHEDULE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"));

    public RecommendOutingCourseResultV2 toResult(OutingCriteria outingCriteria, List<OutingCourse> outingCourses) {
        return new RecommendOutingCourseResultV2(
                new RecommendOutingCourseResultV2.OutingCourseMetadata(
                        outingCourses.size(),
                        outingCriteria.getRequestedLocationName(),
                        outingCriteria
                                .getRequestedDatetime()
                                .atZone(ZoneId.of("Asia/Seoul"))
                                .toLocalDateTime()
                                .toString()),
                outingCourses.stream()
                        .map(outingCourse -> new RecommendOutingCourseResultV2.OutingCourseSnippet(
                                outingCourse.getName(),
                                String.valueOf(outingCourse.getRecommendationScore()),
                                outingCourse.getStops().size(),
                                outingCourse.getDurationMinutes()))
                        .toList(),
                outingCourses.stream()
                        .map(outingCourse -> new RecommendOutingCourseResultV2.OutingCourseDetail(
                                outingCourse.getId(),
                                outingCourse.getName(),
                                String.valueOf(outingCourse.getRecommendationScore()),
                                outingCourse.getDurationMinutes(),
                                outingCourse.getStops().stream()
                                        .map(outingStop -> new RecommendOutingCourseResultV2.OutingCourseStop(
                                                outingStop.getId().sequence(),
                                                outingStop.getOutingGuide().getName(),
                                                outingStop.getOutingGuide().getCategory(),
                                                outingStop.getTravelMinutes(),
                                                outingStop
                                                        .getOutingGuide()
                                                        .getCoordinate()
                                                        .lat(),
                                                outingStop
                                                        .getOutingGuide()
                                                        .getCoordinate()
                                                        .lng()))
                                        .toList()))
                        .toList());
    }

    private int travelMinutes(RecommendCoursesResponseV2.Course course) {
        return course.places().stream()
                .mapToInt(place -> {
                    if (place.travelMinutes() == null || place.travelMinutes() < 0) {
                        throw new ServiceUnavailableException();
                    }

                    return place.travelMinutes();
                })
                .sum();
    }
}
