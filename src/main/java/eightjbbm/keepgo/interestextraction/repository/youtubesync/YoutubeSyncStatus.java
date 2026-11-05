package eightjbbm.keepgo.interestextraction.repository.youtubesync;

public enum YoutubeSyncStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
