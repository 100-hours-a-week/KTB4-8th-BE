package eightjbbm.keepgo.member.entity;

import eightjbbm.keepgo.util.File;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    private String likedVideosPlaylistId;

    public void updateNickname(String newNickname) {
        if (!this.nickname.equals(newNickname)) {
            this.nickname = newNickname;
        }
    }

    public void updateProfileImage(File newProfileImage) {
        if (!this.profileImage.getId().equals(newProfileImage.getId())) {
            this.profileImage = newProfileImage;
        }
    }

    public Member(String nickname) {
        this.nickname = nickname;
    }

    public static Member create(String nickname) {
        return new Member(nickname);
    }
}
