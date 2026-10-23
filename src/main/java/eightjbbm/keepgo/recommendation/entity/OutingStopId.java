package eightjbbm.keepgo.recommendation.entity;

import jakarta.persistence.Embeddable;

import java.io.Serializable;

@Embeddable
public record OutingStopId(
        Long outingCourseId,
        int sequence
) implements Serializable {
}
