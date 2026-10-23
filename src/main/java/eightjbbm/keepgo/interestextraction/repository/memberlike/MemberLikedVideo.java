package eightjbbm.keepgo.interestextraction.repository.memberlike;

import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.sandbox.ds.VideoId;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor
public class MemberLikedVideo {
    @Id @GeneratedValue
    private Long id;
    private String videoId;

    @ManyToOne
    @JoinColumn(name = "member_id")
    private Member member;
    private Instant createDatetime;

    public MemberLikedVideo(String videoId, Member member) {
        this.videoId = videoId;
        this.member = member;
        this.createDatetime = Instant.now();
    }
}
