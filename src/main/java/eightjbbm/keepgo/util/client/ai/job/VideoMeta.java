package eightjbbm.keepgo.util.client.ai.job;

/// AI 분석에 힌트로 전달하는 영상 메타. 값이 없으면 null이며 분석 결과에 저장하지 않는다.
/// `uploadDate`는 한국 시간 기준 `yyyy-MM-dd`다.
public record VideoMeta(String uploadDate, String title, String description) {
    public static final VideoMeta EMPTY = new VideoMeta(null, null, null);
}
