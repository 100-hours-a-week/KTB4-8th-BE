package eightjbbm.keepgo.outingwishlist.application.domain.service;

import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingWishlistQueryRepository;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntry;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntryType;
import eightjbbm.keepgo.outingwishlist.application.domain.model.PageToken;
import eightjbbm.keepgo.outingwishlist.common.InvalidPageTokenException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// 위시리스트의 cursor page를 읽고 다음 cursor를 만든다.
///
@Service
@RequiredArgsConstructor
public class OutingWishlistPageReader {

    private static final int PAGE_SIZE = 10;

    private final OutingWishlistQueryRepository outingWishlistQueryRepository;
    private final PageTokenCodec pageTokenCodec;

    public CursorPage readGuides(
            Long memberId, OutingWishlistEntryType type, String location, boolean ongoing, String cursor) {
        var pageToken = decode(cursor);
        var rows =
                outingWishlistQueryRepository.findAllByCursor(memberId, type, location, ongoing, pageToken, PAGE_SIZE);
        return toPage(rows);
    }

    public CursorPage readCourses(Long memberId, String location, String cursor) {
        var pageToken = decode(cursor);
        var rows = outingWishlistQueryRepository.findCoursesByCursor(memberId, location, pageToken, PAGE_SIZE);
        return toPage(rows);
    }

    private PageToken decode(String cursor) {
        if (cursor == null) {
            return new PageToken(null, null);
        }

        var pageToken = pageTokenCodec.decode(cursor).orElseThrow(InvalidPageTokenException::new);

        if ((pageToken.id() == null) != (pageToken.createDatetime() == null)) {
            throw new InvalidPageTokenException();
        }

        return pageToken;
    }

    private CursorPage toPage(List<OutingWishlistEntry> rows) {
        var entries = rows.subList(0, Math.min(rows.size(), PAGE_SIZE));
        var nextCursor = rows.size() > PAGE_SIZE
                ? pageTokenCodec.encode(new PageToken(
                        entries.getLast().getId(), entries.getLast().getCreateDatetime()))
                : null;
        return new CursorPage(entries, nextCursor);
    }

    public record CursorPage(List<OutingWishlistEntry> entries, String nextCursor) {}
}
