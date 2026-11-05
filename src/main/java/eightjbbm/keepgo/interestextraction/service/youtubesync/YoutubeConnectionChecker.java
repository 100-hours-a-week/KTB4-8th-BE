package eightjbbm.keepgo.interestextraction.service.youtubesync;

/// 스트림을 열기 전에 YouTube 연동 상태만 확인한다. YouTube 조회 자체는 스트림이 열린 뒤에 한다.
public interface YoutubeConnectionChecker {

    /// 연동이 없거나 인증을 갱신할 수 없으면 [YoutubeNotConnectedException]을 던진다.
    void requireConnected(Long memberId);
}
