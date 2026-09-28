package eightjbbm.keepgo.util.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MapCoordinatesToLocationNameResponse(
        Response response
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
            List<Result> result
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            Structure structure
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Structure(
            String level2,
            String level4A
    ) {
    }
}
