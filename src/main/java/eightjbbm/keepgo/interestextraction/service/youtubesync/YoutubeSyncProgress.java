package eightjbbm.keepgo.interestextraction.service.youtubesync;

/// YouTube 동기화 진척률 계산.
///
/// YouTube 조회 구간은 고정 가중치를 쓰고, 나머지 구간은 배치 수가 확정된 뒤 배치마다
/// 균등하게 나눈다. 실패한 배치도 처리된 배치로 계산한다. 임베딩은 진척도에 넣지 않는다.
public final class YoutubeSyncProgress {

    private YoutubeSyncProgress() {}

    /// YouTube 조회가 끝난 직후의 진척률. 분석 대상이 없으면 바로 완료다.
    public static int afterFetch(int targetCount, int fetchWeight) {
        return targetCount == 0 ? 100 : Math.min(100, fetchWeight);
    }

    /// 배치 하나를 커밋한 직후의 진척률.
    public static int afterBatch(int completedBatches, int batchCount, int fetchWeight) {
        if (batchCount <= 0) {
            return 100;
        }
        int remaining = 100 - fetchWeight;
        return Math.min(100, fetchWeight + (completedBatches * remaining) / batchCount);
    }
}
