package eightjbbm.keepgo.util.dto;

public record GetAiVersionResponse(String message, VersionData data) {
    public record VersionData(String environment, String commit) {}
}
