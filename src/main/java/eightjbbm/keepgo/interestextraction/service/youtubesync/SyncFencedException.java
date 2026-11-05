package eightjbbm.keepgo.interestextraction.service.youtubesync;

/// 실행 인스턴스가 더 이상 동기화를 소유하지 않아 쓰기가 거부됐다는 신호.
///
/// heartbeat 만료로 다른 인스턴스의 sweeper가 동기화를 FAILED로 마감한 뒤, 이전 실행자가
/// 배치를 커밋하거나 동기화를 시작하려 할 때 발생한다. 호출자는 추가 쓰기를 중단해야 한다.
public class SyncFencedException extends RuntimeException {

    public SyncFencedException(Long syncId) {
        super("YouTube 동기화 쓰기가 차단되었습니다: syncId=" + syncId);
    }
}
