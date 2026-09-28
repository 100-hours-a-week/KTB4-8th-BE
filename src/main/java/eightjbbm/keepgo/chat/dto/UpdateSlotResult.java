package eightjbbm.keepgo.chat.dto;

public record UpdateSlotResult(
        String userLocationName
) {
    public static UpdateSlotResult from(String userLocationName) {
        return new UpdateSlotResult(userLocationName);
    }

}
