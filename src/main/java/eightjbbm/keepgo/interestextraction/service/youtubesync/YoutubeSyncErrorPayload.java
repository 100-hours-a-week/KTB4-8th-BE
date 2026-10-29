package eightjbbm.keepgo.interestextraction.service.youtubesync;

/// 스트림이 열린 뒤 보내는 오류 이벤트 본문. ProblemDetail과 같은 키를 쓴다.
public record YoutubeSyncErrorPayload(String traceId, String code, String type, String title, String instance) {

    static YoutubeSyncErrorPayload of(String traceId, YoutubeSyncError error) {
        return new YoutubeSyncErrorPayload(
                traceId, error.code(), "about:blank", error.title(), YoutubeSyncConnection.STREAM_PATH);
    }
}
