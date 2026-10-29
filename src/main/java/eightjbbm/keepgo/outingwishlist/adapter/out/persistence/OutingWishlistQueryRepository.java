package eightjbbm.keepgo.outingwishlist.adapter.out.persistence;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntry;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntryType;
import eightjbbm.keepgo.outingwishlist.application.domain.model.PageToken;
import eightjbbm.keepgo.outingwishlist.application.domain.model.QOutingWishlistEntry;
import eightjbbm.keepgo.recommendation.entity.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OutingWishlistQueryRepository {
    private final JPAQueryFactory queryFactory;

    public List<OutingWishlistEntry> findAllByCursor(Long memberId, String type, PageToken pageToken, int size) {
        var ow = QOutingWishlistEntry.outingWishlistEntry;
        var og = QOutingGuide.outingGuide;

        return queryFactory
                .selectFrom(ow)
                .join(ow.outingGuide, og)
                .fetchJoin()
                .where(
                        ow.member.id.eq(memberId),
                        guideTypeEq(og, type),
                        cursorCondition(ow, pageToken.createDatetime(), pageToken.id()),
                        ow.deleteDatetime.isNull())
                .orderBy(ow.createDatetime.desc(), ow.id.desc())
                .limit(size + 1L)
                .fetch();
    }

    public List<OutingWishlistEntry> findCoursesByCursor(
            Long memberId, String location, PageToken pageToken, int size) {
        var entry = QOutingWishlistEntry.outingWishlistEntry;
        var course = QOutingCourse.outingCourse;

        BooleanExpression locationCondition = null;

        if (location != null && !location.isBlank()) {
            String normalizedLocation = location.strip();
            locationCondition =
                    course.location.eq(normalizedLocation).or(course.location.startsWith(normalizedLocation + " "));
        }

        BooleanExpression cursorCondition = null;

        if (pageToken.createDatetime() != null && pageToken.id() != null) {
            cursorCondition = entry.createDatetime
                    .lt(pageToken.createDatetime())
                    .or(entry.createDatetime.eq(pageToken.createDatetime()).and(entry.id.lt(pageToken.id())));
        }

        return queryFactory
                .selectFrom(entry)
                .join(entry.outingCourse, course)
                .fetchJoin()
                .where(
                        entry.member.id.eq(memberId),
                        entry.type.eq(OutingWishlistEntryType.OUTING_COURSE),
                        entry.deleteDatetime.isNull(),
                        locationCondition,
                        cursorCondition)
                .orderBy(entry.createDatetime.desc(), entry.id.desc())
                .limit(size + 1L)
                .fetch();
    }

    public List<OutingStop> findStopsByCourseIds(List<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return List.of();
        }

        var stop = QOutingStop.outingStop;
        var guide = QOutingGuide.outingGuide;

        return queryFactory
                .selectFrom(stop)
                .join(stop.outingGuide, guide)
                .fetchJoin()
                .where(stop.outingCourse.id.in(courseIds))
                .orderBy(stop.outingCourse.id.asc(), stop.id.sequence.asc())
                .fetch();
    }

    public Optional<OutingWishlistEntry> findCourseDetail(Long memberId, Long wishlistEntryId) {
        var entry = QOutingWishlistEntry.outingWishlistEntry;
        var course = QOutingCourse.outingCourse;
        var stop = QOutingStop.outingStop;
        var guide = QOutingGuide.outingGuide;

        var result = queryFactory
                .selectFrom(entry)
                .distinct()
                .join(entry.outingCourse, course)
                .fetchJoin()
                .leftJoin(course.stops, stop)
                .fetchJoin()
                .leftJoin(stop.outingGuide, guide)
                .fetchJoin()
                .where(
                        entry.member.id.eq(memberId),
                        entry.id.eq(wishlistEntryId),
                        entry.type.eq(OutingWishlistEntryType.OUTING_COURSE),
                        entry.deleteDatetime.isNull())
                .fetchOne();

        return Optional.ofNullable(result);
    }

    private BooleanExpression guideTypeEq(QOutingGuide outingGuide, String type) {
        return switch (type) {
            case "place" -> outingGuide.instanceOf(OutingPlace.class);
            case "event" -> outingGuide.instanceOf(OutingEvent.class);
            default -> throw new IllegalArgumentException();
        };
    }

    private BooleanExpression cursorCondition(QOutingWishlistEntry ow, Instant cursorCreateDatetime, Long cursorId) {
        if (cursorCreateDatetime == null && cursorId == null) return null;
        return ow.createDatetime
                .lt(cursorCreateDatetime)
                .or(ow.createDatetime.eq(cursorCreateDatetime).and(ow.id.loe(cursorId)));
    }
}
