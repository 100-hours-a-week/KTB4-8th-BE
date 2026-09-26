package eightjbbm.keepgo.util.dto;

public record RetrieveLikesPlaylistIdResponse(
        ContentDetails contentDetails
) {
    record ContentDetails(
            RelatedPlaylists relatedPlaylists
    ) {
        record RelatedPlaylists(
                String likes
        ) {}
    }

    public String getLikesPlaylistId() {
        return contentDetails().relatedPlaylists().likes();
    }
}
