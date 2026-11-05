package eightjbbm.keepgo.interestextraction.repository.videoanalysis;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoAnalysis {
    public static final int MAX_FAILURE_CODE_LENGTH = 64;

    @Id
    @GeneratedValue
    private Long id;

    private String videoId;

    private Long youtubeSyncId;

    @Enumerated(EnumType.STRING)
    private VideoAnalysisStatus status;

    /// 알 수 없는 AI 응답, 저장 불가 후보만 있음 등 실패 원인을 남긴다.
    @Nullable
    @Column(length = MAX_FAILURE_CODE_LENGTH)
    private String failureCode;

    /// 저장하지 않은 후보의 위치와 사유. 분석 이력에서 제외 근거를 확인할 수 있게 한다.
    @Nullable
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "excluded_candidates", columnDefinition = "json")
    private List<ExcludedCandidate> excludedCandidates;

    private Instant createDatetime;

    private Instant completedAt;

    public VideoAnalysis(String videoId) {
        this(videoId, null);
    }

    public VideoAnalysis(String videoId, Long youtubeSyncId) {
        this.videoId = videoId;
        this.youtubeSyncId = youtubeSyncId;
        this.status = VideoAnalysisStatus.IN_PROGRESS;
        this.createDatetime = Instant.now();
    }

    public void markSuccess() {
        transitionTo(VideoAnalysisStatus.SUCCESS);
    }

    public void markNotFound() {
        transitionTo(VideoAnalysisStatus.NOT_FOUND);
    }

    public void markFailed() {
        transitionTo(VideoAnalysisStatus.FAILED);
    }

    public void markFailed(String failureCode) {
        markFailed();
        this.failureCode = failureCode;
    }

    public void recordExcludedCandidates(List<ExcludedCandidate> excludedCandidates) {
        this.excludedCandidates = List.copyOf(excludedCandidates);
    }

    private void transitionTo(VideoAnalysisStatus status) {
        if (this.status != VideoAnalysisStatus.IN_PROGRESS) {
            throw new IllegalStateException();
        }
        this.status = status;
        this.completedAt = Instant.now();
    }
}
