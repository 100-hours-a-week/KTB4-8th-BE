package eightjbbm.keepgo.outingwishlist.adapter.out.persistence;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingCollectionPrivate;
import eightjbbm.keepgo.outingwishlist.application.domain.model.QOutingCollectionPrivate;
import eightjbbm.keepgo.outingwishlist.application.domain.model.PageToken;
import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.recommendation.entity.QOutingGuide;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

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
                        ocp.createdAt.desc(),
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
        return ocp.createdAt.lt(cursorCreateDatetime)
                .or(
                        ocp.createdAt.eq(cursorCreateDatetime)
                                .and(ocp.id.lte(cursorId)));
    }
}
