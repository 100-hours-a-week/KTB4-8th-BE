package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.recommendation.dto.RequestRecommendationResult;
import eightjbbm.keepgo.recommendation.dto.RequestRecommendationResult.*;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.service.dto.RecommendCoursesResponseV2;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class RecommendationResultMapper {

    private static final DateTimeFormatter SCHEDULE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.of("Asia/Seoul"));

    public RequestRecommendationResult map(
            OutingCriteria criteria,
            List<OutingGuide> candidates,
            RecommendCoursesResponseV2.SuccessData data
    ) {
        var guidesById = candidates.stream()
                .collect(Collectors.toMap(
                        guide -> guide.getId().toString(),
                        Function.identity()
                ));

        String score = data.matchScore() == null
                ? null
                : data.matchScore().toString();

        var snippets = data.courses().stream()
                .map(course -> new RecommendationSnippet(
                        course.title(),
                        score,
                        course.places().size(),
                        travelMinutes(course)
                ))
                .toList();

        var details = data.courses().stream()
                .map(course -> new RecommendationDetail(
                        course.title(),
                        score,
                        travelMinutes(course),
                        IntStream.range(0, course.places().size())
                                .mapToObj(index -> {
                                    var place = course.places().get(index);
                                    var guide = guidesById.get(place.placeId());

                                    if (guide == null) {
                                        throw new ServiceUnavailableException();
                                    }

                                    return new RecommendationDetailItem(
                                            index + 1,
                                            guide.getName(),
                                            guide.getCategory(),
                                            guide.getDescription(),
                                            guide.getCoordinate().lat(),
                                            guide.getCoordinate().lng()
                                    );
                                })
                                .toList()
                ))
                .toList();

        return new RequestRecommendationResult(
                new RecommendationMetadata(
                        data.courses().size(),
                        criteria.getRequestedLocationName(),
                        SCHEDULE_FORMAT.format(criteria.getRequestedDatetime())
                ),
                snippets,
                details
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
