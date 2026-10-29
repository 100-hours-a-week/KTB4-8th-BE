package eightjbbm.keepgo.outingwishlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingWishlistQueryRepository;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntry;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntryType;
import eightjbbm.keepgo.outingwishlist.application.domain.model.PageToken;
import eightjbbm.keepgo.outingwishlist.application.domain.service.OutingWishlistPageReader;
import eightjbbm.keepgo.outingwishlist.application.domain.service.PageTokenCodec;
import eightjbbm.keepgo.outingwishlist.common.InvalidPageTokenException;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("위시리스트 페이지 읽기")
class OutingWishlistPageReaderTest {

    private final OutingWishlistQueryRepository queryRepository = mock(OutingWishlistQueryRepository.class);
    private final PageTokenCodec pageTokenCodec = new PageTokenCodec();
    private final OutingWishlistPageReader reader = new OutingWishlistPageReader(queryRepository, pageTokenCodec);

    @Test
    @DisplayName("한 페이지를 넘는 결과는 10개만 반환하고 마지막 항목으로 다음 커서를 만든다")
    void returnsPageAndCursorFromLastReturnedEntry() {
        var rows = entries(11);
        when(queryRepository.findAllByCursor(
                        eq(1L), eq(OutingWishlistEntryType.PLACE), eq("서울"), eq(false), any(PageToken.class), eq(10)))
                .thenReturn(rows);

        var page = reader.readGuides(1L, OutingWishlistEntryType.PLACE, "서울", false, null);

        assertThat(page.entries()).hasSize(10);
        assertThat(page.entries().getLast().getId()).isEqualTo(10L);
        var next = pageTokenCodec.decode(page.nextCursor()).orElseThrow();
        assertThat(next.id()).isEqualTo(10L);
        assertThat(next.createDatetime()).isEqualTo(Instant.ofEpochSecond(10L));
    }

    @Test
    @DisplayName("정확히 한 페이지면 다음 커서를 만들지 않는다")
    void exactPageHasNoNextCursor() {
        var rows = entries(10);
        when(queryRepository.findAllByCursor(
                        eq(1L), eq(OutingWishlistEntryType.EVENT), eq(null), eq(true), any(PageToken.class), eq(10)))
                .thenReturn(rows);

        var page = reader.readGuides(1L, OutingWishlistEntryType.EVENT, null, true, null);

        assertThat(page.entries()).hasSize(10);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("목록 필터를 조회 adapter에 그대로 전달한다")
    void passesFiltersToQueryAdapter() {
        when(queryRepository.findAllByCursor(
                        eq(1L), eq(OutingWishlistEntryType.PLACE), eq("서울"), eq(false), any(PageToken.class), eq(10)))
                .thenReturn(List.of());

        reader.readGuides(1L, OutingWishlistEntryType.PLACE, "서울", false, null);

        verify(queryRepository)
                .findAllByCursor(
                        eq(1L), eq(OutingWishlistEntryType.PLACE), eq("서울"), eq(false), any(PageToken.class), eq(10));
    }

    @Test
    @DisplayName("해석할 수 없는 커서는 422 예외로 바꾼다")
    void rejectsInvalidCursor() {
        assertThatThrownBy(() -> reader.readGuides(1L, OutingWishlistEntryType.PLACE, null, false, "broken"))
                .isInstanceOf(InvalidPageTokenException.class);
    }

    private List<OutingWishlistEntry> entries(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(this::entry).toList();
    }

    private OutingWishlistEntry entry(long id) {
        var entry = mock(OutingWishlistEntry.class);
        when(entry.getId()).thenReturn(id);
        when(entry.getCreateDatetime()).thenReturn(Instant.ofEpochSecond(id));
        return entry;
    }
}
