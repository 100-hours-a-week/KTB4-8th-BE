package eightjbbm.keepgo.interestextraction.repository.youtubesync;

/// 종료를 기다리는 요청이 영속성 컨텍스트의 1차 캐시를 우회해 읽는 동기화의 최신 상태.
public record YoutubeSyncState(YoutubeSyncStatus status, String errorCode) {

    public boolean isTerminal() {
        return status.isTerminal();
    }
}
