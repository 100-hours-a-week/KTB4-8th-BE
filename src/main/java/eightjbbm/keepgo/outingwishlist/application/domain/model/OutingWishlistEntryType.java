package eightjbbm.keepgo.outingwishlist.application.domain.model;

import eightjbbm.keepgo.recommendation.entity.OutingCourse;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;

public enum OutingWishlistEntryType {
    PLACE,
    EVENT,
    OUTING_COURSE,
    OUTING_WISHLIST_SHARED;

    public static OutingWishlistEntryType getType(OutingGuide outingGuide) {
        if (outingGuide instanceof OutingPlace) return PLACE;
        else return EVENT;
    }

    public static OutingWishlistEntryType getType(OutingCourse outingCourse) {
        return OUTING_COURSE;
    }
}
