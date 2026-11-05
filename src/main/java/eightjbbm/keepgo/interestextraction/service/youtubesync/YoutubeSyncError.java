package eightjbbm.keepgo.interestextraction.service.youtubesync;

/// 동기화 실패 코드와 SSE 이벤트 이름·ProblemDetail 제목의 대응.
public enum YoutubeSyncError {
    YOUTUBE_REAUTHORIZATION_REQUIRED("conflict", "Conflict"),
    YOUTUBE_FETCH_FAILED("bad-gateway", "Bad Gateway"),
    SYNC_BUSY("service-unavailable", "Service Unavailable"),
    INTERNAL_ERROR("internal-server-error", "Internal Server Error");

    private final String event;
    private final String title;

    YoutubeSyncError(String event, String title) {
        this.event = event;
        this.title = title;
    }

    public String event() {
        return event;
    }

    public String title() {
        return title;
    }

    public String code() {
        return name();
    }

    /// 저장된 코드를 읽는다. 알 수 없거나 비어 있으면 내부 오류로 본다.
    public static YoutubeSyncError fromCode(String code) {
        if (code == null) {
            return INTERNAL_ERROR;
        }
        try {
            return valueOf(code);
        } catch (IllegalArgumentException error) {
            return INTERNAL_ERROR;
        }
    }
}
