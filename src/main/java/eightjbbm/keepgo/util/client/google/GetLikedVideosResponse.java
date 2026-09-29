package eightjbbm.keepgo.util.client.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GetLikedVideosResponse(
        String etag,
        List<Item> items,
        @JsonProperty("pageInfo")
        PageInfo pageInfo
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String id
    ) {}

    public record PageInfo(
            @JsonProperty("totalResults")
            Integer totalResults,
            @JsonProperty("resultsPerPage")
            Integer resultsPerPage
    ) {}
}
