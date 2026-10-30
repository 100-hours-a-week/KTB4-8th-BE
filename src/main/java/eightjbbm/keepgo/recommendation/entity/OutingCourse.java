package eightjbbm.keepgo.recommendation.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutingCourse {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private int recommendationScore;

    private int durationMinutes;

    private String location;

    private Instant createDatetime;

    @OneToMany(mappedBy = "outingCourse")
    private List<OutingStop> stops;

    public OutingCourse(
            String name, int durationMinutes, int recommendationScore, String location, List<OutingStop> stops) {
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.recommendationScore = recommendationScore;
        this.location = location;
        this.stops = stops;
        this.createDatetime = Instant.now();
    }

    public void replaceStops(List<OutingStop> newStops) {
        this.stops = newStops;
    }

    public static OutingCourse from(
            String name,
            int durationMinutes,
            int recommendationScore,
            String location,
            List<OutingGuide> outingGuides) {
        var newCourse = new OutingCourse(name, durationMinutes, recommendationScore, location, null);
        newCourse.stops = IntStream.range(0, outingGuides.size())
                .mapToObj(index -> OutingStop.from(newCourse, 0, index + 1, outingGuides.get(index)))
                .toList();
        return newCourse;
    }
}
