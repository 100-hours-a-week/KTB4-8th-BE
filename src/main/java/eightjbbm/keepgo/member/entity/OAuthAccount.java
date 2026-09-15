package eightjbbm.keepgo.member.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor
public class OAuthAccount {
    @Id @GeneratedValue
    private Long id;
    @ManyToOne
    @JoinColumn(name = "member_id")
    private Member member;
    private String provider;
    private String subject;
    private Instant lastLoginAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private String refreshTokenEncrypted;
}
