package eightjbbm.keepgo.interestextraction.service.youtubesync;

/// accepted·progress·success 이벤트의 공통 본문. JSON에서는 snake_case로 직렬화된다.
public record YoutubeSyncEvent(
        Integer videoFetchedCount,
        Integer videoTargetCount,
        Integer videoAnalyzedCount,
        Integer videoFailedCount,
        Integer progress) {

    static YoutubeSyncEvent from(YoutubeSyncSnapshot snapshot) {
        return new YoutubeSyncEvent(
                snapshot.videoFetchedCount(),
                snapshot.videoTargetCount(),
                snapshot.videoAnalyzedCount(),
                snapshot.videoFailedCount(),
                snapshot.progress());
    }
}
