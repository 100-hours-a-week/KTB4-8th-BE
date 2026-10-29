package eightjbbm.keepgo.outingwishlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingWishlist;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntry;
import eightjbbm.keepgo.outingwishlist.application.domain.service.OutingWishlistSyncService;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.util.Coordinate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("분석 결과 위시리스트 최신화")
class OutingWishlistSyncServiceTest {

    private final OutingWishlist outingWishlist = mock(OutingWishlist.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final OutingWishlistSyncService service = new OutingWishlistSyncService(outingWishlist, memberRepository);

    @Test
    @DisplayName("이미 위시리스트에 있는 가이드는 건너뛰고 새 가이드만 저장한다")
    void savesOnlyNewGuides() {
        OutingGuide existing = guide("이미 담은 장소");
        OutingGuide added = guide("새 장소");
        when(outingWishlist.findAllWishlistedOutingGuides(eq(1L), anyList())).thenReturn(List.of(existing));
        Member member = mock(Member.class);
        when(memberRepository.getReferenceById(1L)).thenReturn(member);

        service.sync(1L, List.of(existing, added));

        ArgumentCaptor<List<OutingWishlistEntry>> saved = ArgumentCaptor.captor();
        verify(outingWishlist).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(1);
        assertThat(saved.getValue().getFirst().getOutingGuide()).isSameAs(added);
    }

    @Test
    @DisplayName("분석 결과가 없으면 위시리스트를 조회하지 않는다")
    void skipsEmptyResult() {
        service.sync(1L, List.of());

        verify(outingWishlist, never()).saveAll(anyList());
        verifyNoInteractions(memberRepository);
    }

    private OutingGuide guide(String name) {
        return new OutingPlace("카페", name, "설명", "상세 위치", "서울특별시 성동구 도로명 1", new Coordinate(37.5f, 127.0f), null);
    }
}
