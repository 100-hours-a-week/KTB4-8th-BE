package eightjbbm.keepgo.interestextraction.repository.memberlike;

import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.sandbox.ds.VideoId;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor
public class MemberLikedVideo {
    @Id @GeneratedValue
    private Long id;
    @Embedded
    private VideoId videoId;
    private Member member;
    private Instant createDatetime;

    public MemberLikedVideo(String videoId, Member member) {
        this.videoId = VideoId.fromVideoId(videoId);
        this.member = member;
        this.createDatetime = Instant.now();
    }
}
