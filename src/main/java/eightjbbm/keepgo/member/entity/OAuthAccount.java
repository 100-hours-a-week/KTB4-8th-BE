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
    private String issuer;
    private String subject;
    private Instant lastLoginAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private String refreshTokenEncrypted;

    public OAuthAccount(Member member, String issuer, String subject) {
        this.member = member;
        this.issuer = issuer;
        this.subject = subject;
    }

    public static OAuthAccount create(Member member, String issuer, String subject) {
        return new OAuthAccount(member, issuer, subject);
    }

    public void rotateRefreshToken(String refreshTokenHash) {
        this.refreshTokenEncrypted = refreshTokenHash;
    }

    public void invalidateRefreshToken() {
        this.refreshTokenEncrypted = null;
    }
}
