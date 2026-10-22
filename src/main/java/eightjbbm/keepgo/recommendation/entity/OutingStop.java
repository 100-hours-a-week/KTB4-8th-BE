package eightjbbm.keepgo.recommendation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutingStop {

    @EmbeddedId
    private OutingStopId id;

    @MapsId("outingCourseId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outing_course_id", nullable = false)
    private OutingCourse outingCourse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outing_guide_id", nullable = false)
    private OutingGuide outingGuide;

    protected OutingStop(
            OutingCourse outingCourse,
            int sequence,
            OutingGuide outingGuide
    ) {
        this.id = new OutingStopId(outingCourse.getId(), sequence);
        this.outingCourse = outingCourse;
        this.outingGuide = outingGuide;
    }

    public static OutingStop from(
            OutingCourse outingCourse,
            int sequence,
            OutingGuide outingGuide) {
        return new OutingStop(
                outingCourse,
                sequence,
                outingGuide);
    }
}
