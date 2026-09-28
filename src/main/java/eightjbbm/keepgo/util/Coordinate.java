package eightjbbm.keepgo.util;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record Coordinate(
        @Column(name = "lat", nullable = false)
        Float lat,
        @Column(name = "lng", nullable = false)
        Float lng
) {
    public boolean equals(Coordinate other) {
        if (other == null) {
            return false;
        }
        return (lat.equals(other.lat()) && lng.equals(other.lng()));
    }
}
