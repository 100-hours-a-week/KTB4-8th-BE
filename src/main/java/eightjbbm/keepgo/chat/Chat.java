package eightjbbm.keepgo.chat;

import eightjbbm.keepgo.member.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Chat {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "member_id",
            nullable = false
    )
    private Member member;

    private String content;

    private Boolean isByBot;

    public Chat(Member member, String content, Boolean isByBot) {
        this.member = member;
        this.content = content;
        this.isByBot = isByBot;
    }
}
