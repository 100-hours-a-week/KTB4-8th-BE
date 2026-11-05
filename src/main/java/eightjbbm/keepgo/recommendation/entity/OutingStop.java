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

    private Integer travelMinutes;

    @MapsId("outingCourseId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outing_course_id", nullable = false)
    private OutingCourse outingCourse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outing_guide_id", nullable = false)
    private OutingGuide outingGuide;

    public OutingStop(OutingCourse outingCourse, int travelMinutes, int sequence, OutingGuide outingGuide) {
        this.id = new OutingStopId(outingCourse.getId(), sequence);
        this.travelMinutes = travelMinutes;
        this.outingCourse = outingCourse;
        this.outingGuide = outingGuide;
    }

    public static OutingStop from(OutingCourse outingCourse, int travelMinutes, int sequence, OutingGuide outingGuide) {
        return new OutingStop(outingCourse, travelMinutes, sequence, outingGuide);
    }
}
