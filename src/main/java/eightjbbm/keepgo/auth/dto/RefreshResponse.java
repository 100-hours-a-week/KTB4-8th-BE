package eightjbbm.keepgo.auth.dto;

public record RefreshResponse(
        String accessToken,
        String tokenType,
        Integer expiresIn
) {
    public static RefreshResponse from(String accessToken, String tokenType, Integer expiresIn) {
        return new RefreshResponse(accessToken, tokenType, expiresIn);
    }
}
