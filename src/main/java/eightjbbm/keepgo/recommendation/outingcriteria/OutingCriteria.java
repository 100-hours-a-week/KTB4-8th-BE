package eightjbbm.keepgo.recommendation.outingcriteria;

import eightjbbm.keepgo.util.Coordinate;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter
@AllArgsConstructor
public class OutingCriteria {
    private Long memberId;
    private Coordinate userCoordinate;
    private String requestedLocationName;
    private Instant requestedDatetime;
    private Integer userAvailableTimeMinutes;
    private Set<String> categories;
    @Nullable private String userPreferenceSummary;

    public static OutingCriteria emptyOutingCriteriaWithMemberId(Long memberId) {
        return new OutingCriteria(
                memberId,
                new Coordinate(null, null),
                null,
                null,
                null,
                new HashSet<>(),
                null
        );
    }

    public OutingCriteria update(
            Coordinate coordinate,
            String locationName,
            Instant datetime,
            Integer availableTimeMinutes,
            Set<String> categories,
            String preferenceSummary
    ) {
        return new OutingCriteria(
                this.memberId,
                (coordinate == null) ? this.userCoordinate : coordinate,
                (locationName == null) ? this.requestedLocationName : locationName,
                (datetime == null) ? this.requestedDatetime : datetime,
                (availableTimeMinutes == null) ? this.userAvailableTimeMinutes : availableTimeMinutes,
                (categories == null || categories.isEmpty()) ? this.categories : addCategory(categories),
                (preferenceSummary == null) ? this.userPreferenceSummary : preferenceSummary);
    }

    private Set<String> addCategory(Set<String> categories) {
        Set<String> result = new HashSet<>(this.categories);
        result.addAll(categories);
        return result;
    }
}
