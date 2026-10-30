package eightjbbm.keepgo.embedding.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/// 커밋된 나들이 가이드 하나의 임베딩 작업.
///
/// 행은 신규 가이드를 만드는 트랜잭션에서 같은 트랜잭션으로 기록되고, 폴러가 처리한다.
/// 생성·수정 시각은 DB가 채우므로 여기서는 폴러가 읽는 값만 매핑한다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "outing_guide_embedding_outbox")
public class EmbeddingOutboxEntry {

    @Id
    private Long guideId;

    @Enumerated(EnumType.STRING)
    private EmbeddingOutboxStatus status;

    private int attemptCount;

    private Instant nextAttemptAt;

    private String lastError;
}
