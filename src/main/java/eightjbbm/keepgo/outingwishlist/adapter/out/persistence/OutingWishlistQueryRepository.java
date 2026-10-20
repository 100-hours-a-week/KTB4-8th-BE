package eightjbbm.keepgo.outingwishlist.adapter.out.persistence;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingCollectionPrivate;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntryType;
import eightjbbm.keepgo.outingwishlist.application.domain.model.QOutingCollectionPrivate;
import eightjbbm.keepgo.outingwishlist.application.domain.model.PageToken;
import eightjbbm.keepgo.recommendation.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OutingWishlistQueryRepository {
    private final JPAQueryFactory queryFactory;

    public List<OutingCollectionPrivate> findAllByCursor(
            Long memberId,
            String type,
            PageToken pageToken,
            int size
    ) {
        var ocp = QOutingCollectionPrivate.outingCollectionPrivate;
        var og = QOutingGuide.outingGuide;

        return queryFactory
                .selectFrom(ocp)
                .join(ocp.outingGuide, og).fetchJoin()
                .where(
                        ocp.member.id.eq(memberId),
                        guideTypeEq(og, type),
                        cursorCondition(
                                ocp,
                                pageToken.createDatetime(),
                                pageToken.id())
                )
                .orderBy(
                        ocp.createDatetime.desc(),
                        ocp.id.desc()
                )
                .limit(size + 1L)
                .fetch();
    }

    private BooleanExpression guideTypeEq(
            QOutingGuide outingGuide,
            String type
    ) {
        return switch (type) {
            case "place" -> outingGuide.instanceOf(OutingPlace.class);
            case "event" -> outingGuide.instanceOf(OutingEvent.class);
            default -> throw new IllegalArgumentException();
        };
    }

    private BooleanExpression cursorCondition(
        QOutingCollectionPrivate ocp,
        Instant cursorCreateDatetime,
        Long cursorId
    ) {
        if (cursorCreateDatetime == null && cursorId == null) return null;
        return ocp.createDatetime.lt(cursorCreateDatetime)
                .or(
                        ocp.createDatetime.eq(cursorCreateDatetime)
                                .and(ocp.id.loe(cursorId)));
    }



    public List<OutingCollectionPrivate> findCoursesByCursor(
            Long memberId,
            String location,
            PageToken pageToken,
            int size
    ) {
        var entry = QOutingCollectionPrivate.outingCollectionPrivate;
        var course = QOutingCourse.outingCourse;

        BooleanExpression locationCondition = null;

        if (location != null && !location.isBlank()) {
            String normalizedLocation = location.strip();

            locationCondition = course.address.address.eq(normalizedLocation)
                    .or(course.address.address
                            .startsWith(normalizedLocation + " "));
        }

        BooleanExpression cursorCondition = null;

        if (pageToken.createDatetime() != null && pageToken.id() != null) {
            cursorCondition = entry.createDatetime
                    .lt(pageToken.createDatetime())
                    .or(entry.createDatetime.eq(pageToken.createDatetime())
                            .and(entry.id.lt(pageToken.id())));
        }

        return queryFactory
                .selectFrom(entry)
                .join(entry.outingCourse, course).fetchJoin()
                .where(
                        entry.member.id.eq(memberId),
                        entry.type.eq(OutingWishlistEntryType.OUTING_COURSE),
                        entry.deleteDatetime.isNull(),
                        locationCondition,
                        cursorCondition
                )
                .orderBy(
                        entry.createDatetime.desc(),
                        entry.id.desc()
                )
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
                .join(stop.outingGuide, guide).fetchJoin()
                .where(stop.outingCourse.id.in(courseIds))
                .orderBy(
                        stop.outingCourse.id.asc(),
                        stop.id.sequence.asc()
                )
                .fetch();
    }

    public Optional<OutingCollectionPrivate> findCourseDetail(
            Long memberId,
            Long wishlistEntryId
    ) {
        var entry = QOutingCollectionPrivate.outingCollectionPrivate;
        var course = QOutingCourse.outingCourse;
        var stop = QOutingStop.outingStop;
        var guide = QOutingGuide.outingGuide;

        var result = queryFactory
                .selectFrom(entry)
                .distinct()
                .join(entry.outingCourse, course).fetchJoin()
                .leftJoin(course.stops, stop).fetchJoin()
                .leftJoin(stop.outingGuide, guide).fetchJoin()
                .where(
                        entry.member.id.eq(memberId),
                        entry.id.eq(wishlistEntryId),
                        entry.type.eq(OutingWishlistEntryType.OUTING_COURSE),
                        entry.deleteDatetime.isNull()
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }
}
