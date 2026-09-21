package eightjbbm.keepgo.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Integer expiresIn
) {
    public static LoginResponse from(String accessToken, String tokenType, Integer expiresIn) {
        return new LoginResponse(accessToken, tokenType, expiresIn);
    }
}
