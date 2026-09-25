package eightjbbm.keepgo.chat.dto;

public record UpdateSlotResponse(
        String userLocationName
) {
    public static UpdateSlotResponse from(String userLocationName) {
        return new UpdateSlotResponse(userLocationName);
    }
}
