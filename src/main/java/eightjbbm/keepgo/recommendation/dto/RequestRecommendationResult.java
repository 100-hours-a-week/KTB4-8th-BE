package eightjbbm.keepgo.recommendation.dto;

import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.util.cache.slot.SlotValue;
import eightjbbm.keepgo.util.dto.RecommendCourseResponse;

import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

public record RequestRecommendationResult(
        RecommendationMetadata metadata,
        List<RecommendationSnippet> snippets,
        List<RecommendationDetail> details

) {
    public record RecommendationMetadata(
            Integer totalCourseCount,
            String location,
            String scheduledTime
    ) {}

    public record RecommendationSnippet(
            String title,
            String recommendationScore,
            Integer totalPlaceCount,
            Integer totalTravelTime
    ) {
        public static RecommendationSnippet from(RecommendCourseResponse.Success.RecommendCourse course) {
            return new RecommendationSnippet(
                    course.title(),
                    String.valueOf(100),
                    course.places().size(),
                    course.places()
                            .stream()
                            .mapToInt(RecommendCourseResponse.Success.RecommendPlace::travelMinutes)
                            .sum()
            );
        }
    }

    public record RecommendationDetail(
            String title,
            String recommendationScore,
            Integer totalTravelTime,
            List<RecommendationDetailItem> items
    ) {
        public static RecommendationDetail from(
                RecommendCourseResponse.RecommendCourse course,
                Function<RecommendCourseResponse.RecommendPlace, OutingPlace> extractor
        ) {
            return new RecommendationDetail(
                    course.title(),
                    String.valueOf(100),
                    course.places().stream().mapToInt(RecommendCourseResponse.RecommendPlace::travelMinutes).sum(),
                    IntStream.range(0, course.places().size())
                            .mapToObj(k -> RecommendationDetailItem.from(
                                    k,
                                    course.places().get(k),
                                    extractor.apply(course.places().get(k))
                            )).toList()
            );
        }
    }

    public record RecommendationDetailItem(
            Integer sequence,
            String name,
            String category,
            String description,
            Float lat,
            Float lng
    ) {
        public static RecommendationDetailItem from(int sequence, RecommendCourseResponse.RecommendPlace place, OutingPlace outingPlace) {
            return new RecommendationDetailItem(
                    sequence,
                    place.placeName(),
                    outingPlace.getCategory(),
                    place.travelMinutes().toString(),
                    /*
                    outingPlace.getLat(),
                    outingPlace.getLng()

                     */
                    0.0f,
                    0.0f
            );
        }
    }

    public static RequestRecommendationResult from(
            SlotValue slot,
            RecommendCourseResponse.Success response,
            Function<RecommendCourseResponse.RecommendPlace, OutingPlace> extractor) {
        return new RequestRecommendationResult(
                new RecommendationMetadata(
                        response.data().courses().size(),
                        slot.getRegion(),
                        slot.getDatetime().toString()
                ),
                response.data().courses().stream().map(RecommendationSnippet::from).toList(),
                response.data().courses().stream().map(k -> RecommendationDetail.from(k, extractor)).toList()
        );
    }
}
