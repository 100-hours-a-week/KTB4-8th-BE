package eightjbbm.keepgo.outingwishlist.application.domain.service.dto;

public record AddOutingCourseResult(
        Long addedOutingWishlistEntryId
) {
    public static AddOutingCourseResult from(
            Long addedOutingWishlistEntryId
    ) {
        return new AddOutingCourseResult(addedOutingWishlistEntryId);
    }
}
