package eightjbbm.keepgo.recommendation.entity;

import eightjbbm.keepgo.sandbox.ds.Address;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.IntStream;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutingCourse {

    @Id @GeneratedValue
    private Long id;

    private String name;

    private int durationHours;

    private Address address;

    @OneToMany(mappedBy = "outingCourse")
    private List<OutingStop> stops;

    private OutingCourse(
            String name,
            int durationHours,
            Address address,
            List<OutingStop> stops
    ) {
        this.name = name;
        this.durationHours = durationHours;
        this.address = address;
        this.stops = stops;
    }

    public static OutingCourse from(
            String name,
            int durationHours,
            Address address,
            List<OutingGuide> outingGuides
    ) {
        var newCourse = new OutingCourse(
                name,
                durationHours,
                address,
                null);
        newCourse.stops = IntStream.range(0, outingGuides.size())
                .mapToObj(index ->
                        OutingStop.from(
                                newCourse,
                                index + 1,
                                outingGuides.get(index)))
                .toList();
        return newCourse;
    }
}