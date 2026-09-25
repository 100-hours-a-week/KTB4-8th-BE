package eightjbbm.keepgo.util;

public record Coordinate(
        Float lat,
        Float lng
) {
    public boolean equals(Coordinate other) {
        return (lat.equals(other.lat()) && lng.equals(other.lng()));
    }
}
