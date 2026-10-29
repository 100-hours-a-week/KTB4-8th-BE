package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingWishlist;
import eightjbbm.keepgo.recommendation.RecommendationProperties;
import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/// 추천 요청의 위치·시간 조건으로 후보 가이드를 고른다.
///
@Slf4j
@Service
@EnableConfigurationProperties(RecommendationProperties.class)
public class RecommendationCandidateSelector {

    private static final int MAX_CANDIDATES = 50;

    private final OutingWishlist outingWishlist;
    private final KakaoPlaceSearch kakaoPlaceSearch;
    private final RecommendationProperties properties;

    public RecommendationCandidateSelector(
            OutingWishlist outingWishlist, KakaoPlaceSearch kakaoPlaceSearch, RecommendationProperties properties) {
        if (!Double.isFinite(properties.candidateRadiusKm()) || properties.candidateRadiusKm() <= 0) {
            throw new IllegalArgumentException("추천 반경은 양수여야 합니다.");
        }

        this.outingWishlist = outingWishlist;
        this.kakaoPlaceSearch = kakaoPlaceSearch;
        this.properties = properties;
    }

    public List<OutingGuide> select(Long memberId, OutingCriteria criteria) {
        var origin = requestOrigin(criteria.getRequestedLocationName());
        var requestedAt = criteria.getRequestedDatetime();

        return outingWishlist.findRecommendationCandidates(memberId).stream()
                .filter(guide ->
                        guide.getCoordinate() != null && guide.getCoordinate().isValid())
                .filter(guide -> isWithinEventPeriod(guide, requestedAt))
                .filter(guide -> distanceKm(origin, guide.getCoordinate()) <= properties.candidateRadiusKm())
                .sorted(Comparator.comparingDouble((OutingGuide guide) -> distanceKm(origin, guide.getCoordinate()))
                        .thenComparing(OutingGuide::getId))
                .limit(MAX_CANDIDATES)
                .toList();
    }

    private Coordinate requestOrigin(String requestedLocationName) {
        try {
            return kakaoPlaceSearch.getCoordinates(requestedLocationName);
        } catch (Exception error) {
            log.warn("추천 기준 위치를 찾지 못했습니다: location={}", requestedLocationName, error);
            throw new ServiceUnavailableException();
        }
    }

    private double distanceKm(Coordinate from, Coordinate to) {
        double fromLat = Math.toRadians(from.lat());
        double toLat = Math.toRadians(to.lat());

        double deltaLat = toLat - fromLat;
        double deltaLng = Math.toRadians((double) to.lng() - from.lng());

        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(fromLat) * Math.cos(toLat) * Math.pow(Math.sin(deltaLng / 2), 2);

        return 6371.0088 * 2 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, a))));
    }

    private boolean isWithinEventPeriod(OutingGuide guide, Instant requestedAt) {
        if (!(guide instanceof OutingEvent event)) {
            return true;
        }

        Instant start = event.getStartDatetime();
        Instant end = event.getEndDatetime();

        boolean hasStarted = start == null || !requestedAt.isBefore(start);

        boolean hasNotEnded = end == null || requestedAt.isBefore(end);

        return hasStarted && hasNotEnded;
    }
}
