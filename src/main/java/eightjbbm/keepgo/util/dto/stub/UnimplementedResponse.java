package eightjbbm.keepgo.util.dto.stub;

public record UnimplementedResponse(
        String message
) {
    public static UnimplementedResponse from(
            String message
    ) {
        return new UnimplementedResponse(message);
    }
}
