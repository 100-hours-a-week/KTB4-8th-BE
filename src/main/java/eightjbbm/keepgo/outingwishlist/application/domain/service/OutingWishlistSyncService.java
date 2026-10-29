package eightjbbm.keepgo.outingwishlist.application.domain.service;

import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingWishlist;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntry;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 분석으로 추출된 나들이 가이드를 회원의 여행 위시리스트에 최신화한다.
///
@Service
@RequiredArgsConstructor
public class OutingWishlistSyncService {

    private final OutingWishlist outingWishlist;
    private final MemberRepository memberRepository;

    @Transactional
    public void sync(Long memberId, List<OutingGuide> analyzedGuides) {
        if (analyzedGuides.isEmpty()) return;

        Set<OutingGuide> wishlistedGuides =
                new HashSet<>(outingWishlist.findAllWishlistedOutingGuides(memberId, analyzedGuides));

        Member member = memberRepository.getReferenceById(memberId);
        outingWishlist.saveAll(analyzedGuides.stream()
                .distinct()
                .filter(guide -> !wishlistedGuides.contains(guide))
                .map(guide -> OutingWishlistEntry.from(member, guide))
                .toList());
    }
}
