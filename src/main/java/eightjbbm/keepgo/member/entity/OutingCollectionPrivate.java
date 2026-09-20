package eightjbbm.keepgo.member.entity;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    public OutingCollectionPrivate(Member member, OutingGuide outingGuide) {
        this.member = member;
        this.outingGuide = outingGuide;
    }
}
