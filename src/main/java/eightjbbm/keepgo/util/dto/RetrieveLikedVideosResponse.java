package eightjbbm.keepgo.util.dto;

import java.util.List;

public record RetrieveLikedVideosResponse(
        String etag,
        String nextPageToken,
        String prevPageToken,
        RlvPageInfo pageInfo,
        List<PlaylistItem> items
) {
    record RlvPageInfo(
            Integer totalResults,
            Integer resultsPerPage
    ) {}

    record PlaylistItem(
            ContentDetails contentDetails
    ) {
        record ContentDetails(
                String videoId
        ) {}
    }

    public List<String> getVideoIds() {
        return items.stream().map(k -> k.contentDetails().videoId()).toList();
    }
}
