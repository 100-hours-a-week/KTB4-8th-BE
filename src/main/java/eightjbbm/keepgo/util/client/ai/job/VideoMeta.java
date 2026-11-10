package eightjbbm.keepgo.util.client.ai.job;

import java.time.LocalDate;

/// AI 분석에 힌트로 전달하는 영상 메타. 값이 없으면 null이며 분석 결과에 저장하지 않는다.
/// `uploadDate`는 한국 시간 기준 날짜이며 요청에서는 `yyyy-MM-dd` 문자열로 직렬화된다.
public record VideoMeta(LocalDate uploadDate, String title, String description) {
    public static final VideoMeta EMPTY = new VideoMeta(null, null, null);
}
