package eightjbbm.keepgo.outingwishlist.application.domain.service;

import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingWishlistQueryRepository;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingCollectionPrivate;
import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingCollectionPrivateRepository;
import eightjbbm.keepgo.outingwishlist.application.domain.model.PageToken;
import eightjbbm.keepgo.outingwishlist.application.domain.service.dto.*;
import eightjbbm.keepgo.outingwishlist.common.EventNotFoundException;
import eightjbbm.keepgo.outingwishlist.common.InvalidPageTokenException;
import eightjbbm.keepgo.outingwishlist.common.PlaceNotFoundException;
import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.util.exception.UnprocessableContentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OutingWishlistService {

    private final OutingCollectionPrivateRepository outingWishlistRepository;
    private final OutingWishlistQueryRepository outingWishlistQueryRepository;
    private final PageTokenCodec pageTokenCodec;

    private final static int PAGE_SIZE = 10;

    /// 여행 위시리스트에 추가한 장소 목록 조회 API
    ///
    @Transactional(readOnly = true)
    public GetPlaceListResult getPlaceList(
            GetPlaceListCommand command
    ) {
        var pageToken = (command.cursor() == null)
                ? new PageToken(null, null)
                : pageTokenCodec.decode(command.cursor())
                .orElseThrow(InvalidPageTokenException::new);
        List<OutingCollectionPrivate> wishlistEntries = outingWishlistQueryRepository
                .findAllByCursor(
                        command.memberId(),
                        "place",
                        pageToken,
                        PAGE_SIZE);
        String nextCursor = (wishlistEntries.size() <= PAGE_SIZE)
                ? null
                : pageTokenCodec.encode(
                        new PageToken(
                                wishlistEntries.getLast().getId(),
                                wishlistEntries.getLast().getCreatedAt()));
        return GetPlaceListResult.from(
                wishlistEntries.subList(
                        0,
                        Math.min(
                                PAGE_SIZE,
                                wishlistEntries.size())),
                nextCursor);
    }

    /// 여행 위시리스트에 추가한 장소 상세 조회 API
    ///
    @Transactional(readOnly = true)
    public GetPlaceDetailResult getPlaceDetail(
            GetPlaceDetailCommand command
    ) {
        OutingCollectionPrivate wishlistEntry = outingWishlistRepository
                .findByMemberIdAndTypeAndOutingWishlistEntryId(
                        command.memberId(),
                        command.placeId())
                .orElseThrow(PlaceNotFoundException::new);
        if (!(wishlistEntry.getOutingGuide() instanceof OutingPlace)) {
            throw new UnprocessableContentException();
        }
        return GetPlaceDetailResult.from(wishlistEntry);
    }

    /// 여행 위시리스트에 추가한 행사 목록 조회 API
    ///
    @Transactional(readOnly = true)
    public GetEventListResult getEventList(
            GetEventListCommand command
    ) {
        var pageToken = command.cursor() == null
                ? new PageToken(null, null)
                : pageTokenCodec.decode(command.cursor())
                .orElseThrow(InvalidPageTokenException::new);
        List<OutingCollectionPrivate> wishlistEntries = outingWishlistQueryRepository
                .findAllByCursor(
                        command.memberId(),
                        "event",
                        pageToken,
                        PAGE_SIZE);
        String nextCursor = (wishlistEntries.size() <= PAGE_SIZE)
                ? null
                : pageTokenCodec.encode(
                new PageToken(
                        wishlistEntries.getLast().getId(),
                        wishlistEntries.getLast().getCreatedAt()));
        return GetEventListResult.from(
                wishlistEntries.subList(
                        0,
                        Math.min(
                                PAGE_SIZE,
                                wishlistEntries.size())),
                nextCursor);
    }

    /// 여행 위시리스트에 추가한 행사 상세 조회 API
    ///
    @Transactional(readOnly = true)
    public GetEventDetailResult getEventDetail(
            GetEventDetailCommand command
    ) {
        OutingCollectionPrivate wishlistEntry = outingWishlistRepository
                .findByMemberIdAndTypeAndOutingWishlistEntryId(
                        command.memberId(),
                        command.eventId())
                .orElseThrow(EventNotFoundException::new);
        if (!(wishlistEntry.getOutingGuide() instanceof OutingEvent)) {
            throw new UnprocessableContentException();
        }
        return GetEventDetailResult.from(wishlistEntry);
    }

    @Transactional(readOnly = true)
    public GetOutingCourseListResult getOutingCourseList(
            GetOutingCourseListCommand command
    ) {

    }

    @Transactional(readOnly = true)
    public GetOutingCourseDetailResult getOutingCourseDetail(
            GetOutingCourseDetailCommand command
    ) {

    }
}
