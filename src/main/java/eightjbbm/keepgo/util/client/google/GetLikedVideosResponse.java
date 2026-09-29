package eightjbbm.keepgo.util.client.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GetLikedVideosResponse(
        List<Item> items,
        String etag,
        PageInfo pageInfo
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
        String id
    ) {}

    public record PageInfo(
        Integer resultPerPage,
        Integer totalResults
    ) {}
}
