package eightjbbm.keepgo.member.entity;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor
public class OutingCollectionPrivate {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne
    @JoinColumn(name = "guide_id")
    private OutingGuide outingGuide;

    private Instant createdAt;

    public OutingCollectionPrivate(Member member, OutingGuide outingGuide) {
        this.member = member;
        this.outingGuide = outingGuide;
    }

    /// 저장 시각은 코스 추천의 취향 계산(history_place_ids.saved_at)에 쓰인다.
    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
