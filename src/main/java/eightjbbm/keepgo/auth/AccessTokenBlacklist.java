package eightjbbm.keepgo.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class AccessTokenBlacklist {
    @Id @GeneratedValue
    private Long id;
    private String accessToken;

    public AccessTokenBlacklist(String accessToken) {
        this.accessToken = accessToken;
    }
}
