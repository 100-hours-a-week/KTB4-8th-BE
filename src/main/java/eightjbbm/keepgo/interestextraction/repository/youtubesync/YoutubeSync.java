package eightjbbm.keepgo.interestextraction.repository.youtubesync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/// 회원의 YouTube 동기화 하나. SSE 연결과 실행 인스턴스가 같은 행을 공유한다.
///
/// `active_member_id` 생성 컬럼과 유일 제약은 DB가 관리하므로 엔티티에 매핑하지 않는다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YoutubeSync {

    @Id
    @GeneratedValue
    private Long id;

    private Long memberId;

    private String instanceId;

    private String traceId;

    @Enumerated(EnumType.STRING)
    private YoutubeSyncStatus status;

    private long eventSeq;

    private Integer videoFetchedCount;

    private Integer videoTargetCount;

    private Integer videoAnalyzedCount;

    private Integer videoFailedCount;

    private int progress;

    private String errorCode;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant finishedAt;

    /// heartbeat는 native UPDATE로만 갱신하고 insert 시에는 DB 기본값을 쓴다.
    @Column(name = "heartbeat_at", insertable = false, updatable = false)
    private Instant heartbeatAt;

    public static YoutubeSync queued(Long memberId, String instanceId, String traceId) {
        YoutubeSync sync = new YoutubeSync();
        sync.memberId = memberId;
        sync.instanceId = instanceId;
        sync.traceId = traceId;
        sync.status = YoutubeSyncStatus.QUEUED;
        sync.eventSeq = 1;
        sync.progress = 0;
        sync.createdAt = Instant.now();
        sync.updatedAt = sync.createdAt;
        return sync;
    }
}
