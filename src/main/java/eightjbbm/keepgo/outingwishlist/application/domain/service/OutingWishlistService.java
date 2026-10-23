package eightjbbm.keepgo.outingwishlist.application.domain.service;

import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingWishlistQueryRepository;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingCollectionPrivate;
import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingCollectionPrivateRepository;
import eightjbbm.keepgo.outingwishlist.application.domain.model.PageToken;
import eightjbbm.keepgo.outingwishlist.application.domain.service.dto.*;
import eightjbbm.keepgo.outingwishlist.common.EventNotFoundException;
import eightjbbm.keepgo.outingwishlist.common.InvalidPageTokenException;
import eightjbbm.keepgo.outingwishlist.common.OutingCourseNotFoundException;
import eightjbbm.keepgo.outingwishlist.common.PlaceNotFoundException;
import eightjbbm.keepgo.recommendation.entity.OutingCourse;
import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.recommendation.repository.OutingCourseRepository;
import eightjbbm.keepgo.util.exception.UnprocessableContentException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OutingWishlistService {

    private final MemberRepository memberRepository;
    private final OutingCourseRepository outingCourseRepository;
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
                                wishlistEntries.getLast().getCreateDatetime()));
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
                        wishlistEntries.getLast().getCreateDatetime()));
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
        var pageToken = command.cursor() == null
                ? new PageToken(null, null)
                : pageTokenCodec.decode(command.cursor())
                .orElseThrow(InvalidPageTokenException::new);

        if ((pageToken.id() == null)
                != (pageToken.createDatetime() == null)) {
            throw new InvalidPageTokenException();
        }

        var entries = outingWishlistQueryRepository.findCoursesByCursor(
                command.memberId(),
                command.location(),
                pageToken,
                PAGE_SIZE);

        boolean hasNext = entries.size() > PAGE_SIZE;

        var pageEntries = entries.subList(
                0,
                Math.min(entries.size(), PAGE_SIZE));

        String nextCursor = null;

        if (hasNext) {
            var lastEntry = pageEntries.getLast();

            nextCursor = pageTokenCodec.encode(
                    new PageToken(
                            lastEntry.getId(),
                            lastEntry.getCreateDatetime()));
        }

        var courseIds = pageEntries.stream()
                .map(entry -> entry.getOutingCourse().getId())
                .distinct()
                .toList();

        var stops = outingWishlistQueryRepository
                .findStopsByCourseIds(courseIds);

        return GetOutingCourseListResult.from(
                pageEntries,
                stops,
                nextCursor);
    }

    @Transactional(readOnly = true)
    public GetOutingCourseDetailResult getOutingCourseDetail(
            GetOutingCourseDetailCommand command
    ) {
        var entry = outingWishlistQueryRepository
                .findCourseDetail(
                        command.memberId(),
                        command.outingCourseId())
                .orElseThrow(
                        eightjbbm.keepgo.util.exception.NotFoundException::new);

        return GetOutingCourseDetailResult.from(
                entry,
                java.time.Instant.now());
    }

    @Transactional
    public AddOutingCourseResult addOutingCourse(
            AddOutingCourseCommand command
    ) {
        var savedOutingWishlistEntry = outingWishlistRepository.save(
                OutingCollectionPrivate.from(
                        memberRepository.getReferenceById(command.memberId()),
                        outingCourseRepository.getReferenceById(command.outingCourseId())));
        return AddOutingCourseResult.from(
                savedOutingWishlistEntry.getId()
        );
    }

    @Transactional
    public void removePlace(
        Long memberId,
        Long placeId
    ) {
        var wishlistEntry = outingWishlistRepository
                .findByMemberIdAndTypeAndOutingWishlistEntryId(memberId, placeId)
                .orElseThrow(PlaceNotFoundException::new);
        wishlistEntry.delete();
    }

    @Transactional
    public void removeEvent(
        Long memberId,
        Long eventId
    ) {
        var wishlistEntry = outingWishlistRepository
                .findByMemberIdAndTypeAndOutingWishlistEntryId(memberId, eventId)
                .orElseThrow(EventNotFoundException::new);
        wishlistEntry.delete();
    }

    @Transactional
    public void removeOutingCourse(
        Long memberId,
        Long outingCourseId
    ) {
        var wishlistEntry = outingWishlistRepository
                .findByMemberIdAndTypeAndOutingWishlistEntryId(memberId, outingCourseId)
                .orElseThrow(OutingCourseNotFoundException::new);
        wishlistEntry.delete();
    }
}
