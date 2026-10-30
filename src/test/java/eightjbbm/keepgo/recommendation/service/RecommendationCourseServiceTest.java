package eightjbbm.keepgo.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eightjbbm.keepgo.recommendation.entity.OutingCourse;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.entity.OutingStop;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.repository.OutingCourseRepository;
import eightjbbm.keepgo.recommendation.repository.OutingGuideRepository;
import eightjbbm.keepgo.recommendation.repository.OutingStopRepository;
import eightjbbm.keepgo.recommendation.service.dto.RecommendCoursesResponseV2;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("추천 코스 저장")
class RecommendationCourseServiceTest {

    private final OutingCourseRepository courseRepository = mock(OutingCourseRepository.class);
    private final OutingStopRepository stopRepository = mock(OutingStopRepository.class);
    private final OutingGuideRepository guideRepository = mock(OutingGuideRepository.class);
    private final RecommendationCourseService service =
            new RecommendationCourseService(courseRepository, stopRepository, guideRepository);

    @Test
    @DisplayName("소요 시간은 분 단위로 저장하고 정류장 순번은 1부터 매긴다")
    void storesDurationInMinutesAndNumbersStopsFromOne() {
        var first = guide(1L);
        var second = guide(2L);
        when(guideRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(first, second));
        when(courseRepository.save(any(OutingCourse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var course = service.saveCourses(criteria(), data(course(places("1", "2"), 95)))
                .getFirst();

        assertThat(course.getDurationMinutes()).isEqualTo(95);

        var stops = capturedStops();
        assertThat(stops).extracting(stop -> stop.getId().sequence()).containsExactly(1, 2);
        assertThat(stops).extracting(OutingStop::getOutingGuide).containsExactly(first, second);
    }

    @Test
    @DisplayName("같은 장소가 반복돼도 각 정류장은 고유한 순번을 갖는다")
    void repeatedPlaceStillGetsItsOwnSequence() {
        var guide = guide(1L);
        when(guideRepository.findAllById(List.of(1L))).thenReturn(List.of(guide));
        when(courseRepository.save(any(OutingCourse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveCourses(criteria(), data(course(places("1", "1"), 30)));

        assertThat(capturedStops()).extracting(stop -> stop.getId().sequence()).containsExactly(1, 2);
    }

    @Test
    @DisplayName("AI가 모르는 장소 id를 주면 정류장을 저장하지 않고 실패한다")
    void unknownPlaceIdFailsInsteadOfSavingStopWithoutGuide() {
        when(guideRepository.findAllById(List.of(99L))).thenReturn(List.of());
        when(courseRepository.save(any(OutingCourse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.saveCourses(criteria(), data(course(places("99"), 30))))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(stopRepository, org.mockito.Mockito.never()).saveAll(any());
    }

    private List<OutingStop> capturedStops() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<OutingStop>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(stopRepository).saveAll(captor.capture());
        var result = new java.util.ArrayList<OutingStop>();
        captor.getValue().forEach(result::add);
        return result;
    }

    private OutingGuide guide(Long id) {
        var guide = mock(OutingGuide.class);
        when(guide.getId()).thenReturn(id);
        return guide;
    }

    private OutingCriteria criteria() {
        return OutingCriteria.emptyOutingCriteriaWithMemberId(1L);
    }

    private RecommendCoursesResponseV2.SuccessData data(RecommendCoursesResponseV2.Course course) {
        return new RecommendCoursesResponseV2.SuccessData(null, 85, List.of(course));
    }

    private RecommendCoursesResponseV2.Course course(List<RecommendCoursesResponseV2.Place> places, int minutes) {
        return new RecommendCoursesResponseV2.Course("코스", places, minutes);
    }

    private List<RecommendCoursesResponseV2.Place> places(String... placeIds) {
        return java.util.Arrays.stream(placeIds)
                .map(placeId -> new RecommendCoursesResponseV2.Place(placeId, "장소 " + placeId, 10, null, 30, null))
                .toList();
    }
}
