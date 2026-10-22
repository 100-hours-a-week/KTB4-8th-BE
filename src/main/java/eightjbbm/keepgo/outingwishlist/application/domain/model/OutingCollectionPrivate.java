package eightjbbm.keepgo.outingwishlist.application.domain.model;

import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.recommendation.entity.OutingCourse;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutingCollectionPrivate {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(value = EnumType.STRING)
    @Column(name = "type", nullable = false)
    private OutingWishlistEntryType type;

    @ManyToOne
    @JoinColumn(name = "guide_id")
    @Nullable
    private OutingGuide outingGuide;

    @ManyToOne
    @JoinColumn(name = "course_id")
    @Nullable
    private OutingCourse outingCourse;

    @Column(name = "created_at", nullable = false)
    private Instant createDatetime;

    @Column(name = "deleted_at")
    private Instant deleteDatetime;

    protected OutingCollectionPrivate(
            Member member,
            OutingWishlistEntryType type,
            @Nullable OutingGuide outingGuide,
            @Nullable OutingCourse outingCourse
    ) {
        this.member = member;
        this.type = type;
        this.outingGuide = outingGuide;
        this.outingCourse = outingCourse;
        this.createDatetime = Instant.now();
    }

    public static OutingCollectionPrivate from(Member member, OutingGuide outingGuide) {
        return new OutingCollectionPrivate(
                member,
                OutingWishlistEntryType.getType(outingGuide),
                outingGuide,
                null
        );
    }

    public static OutingCollectionPrivate from(Member member, OutingCourse outingCourse) {
        return new OutingCollectionPrivate(
                member,
                OutingWishlistEntryType.getType(outingCourse),
                null,
                outingCourse
        );
    }

    public void delete() {
        this.deleteDatetime = Instant.now();
    }

    public boolean isDeleted() {
        return deleteDatetime != null;
    }
}
