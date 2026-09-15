package eightjbbm.keepgo.member.entity;

import eightjbbm.keepgo.file.entity.File;
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
}
