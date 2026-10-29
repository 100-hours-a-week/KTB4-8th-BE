package eightjbbm.keepgo.interestextraction.service.youtubesync;

import eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSync;
import eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncStatus;

/// SSE 연결이 공유하는 동기화 행의 한 시점. 아직 모르는 값은 null이다.
public record YoutubeSyncSnapshot(
        Long id,
        Long memberId,
        YoutubeSyncStatus status,
        long eventSeq,
        Integer videoFetchedCount,
        Integer videoTargetCount,
        Integer videoAnalyzedCount,
        Integer videoFailedCount,
        Integer progress,
        String errorCode,
        String traceId) {

    static YoutubeSyncSnapshot from(YoutubeSync sync) {
        return new YoutubeSyncSnapshot(
                sync.getId(),
                sync.getMemberId(),
                sync.getStatus(),
                sync.getEventSeq(),
                sync.getVideoFetchedCount(),
                sync.getVideoTargetCount(),
                sync.getVideoAnalyzedCount(),
                sync.getVideoFailedCount(),
                sync.getProgress(),
                sync.getErrorCode(),
                sync.getTraceId());
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }
}
