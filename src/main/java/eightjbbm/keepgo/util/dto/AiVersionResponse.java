package eightjbbm.keepgo.util.dto;

public record AiVersionResponse(String message, VersionData data) {
    public record VersionData(String environment, String commit) {}
}
