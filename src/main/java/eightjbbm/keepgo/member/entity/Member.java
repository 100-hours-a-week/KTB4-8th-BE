package eightjbbm.keepgo.member.entity;

import eightjbbm.keepgo.util.file.File;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor
public class Member {

    @Id @GeneratedValue
    private Long id;
    private String nickname;

    @OneToOne
    @JoinColumn(name = "image_id")
    private File profileImage;

    private Instant deletedAt;

    @Setter
    private String likedVideosPlaylistId;

    public void updateNickname(String newNickname) {
        this.nickname = newNickname;
    }

    public void updateProfileImage(File newProfileImage) {
        this.profileImage = newProfileImage;
    }

    public Member(String nickname) {
        String finalNickname = "새 사용자";
        if (nickname != null) {
            finalNickname = nickname;
        }
        this.nickname = finalNickname;
    }

    public static Member create(String nickname) {
        return new Member(nickname);
    }
}
