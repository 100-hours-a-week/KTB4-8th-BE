package eightjbbm.keepgo.interestextraction.repository.videoanalysis;

/// 분석 응답에서 저장하지 않은 후보의 위치(0부터 시작하는 순서)와 사유.
public record ExcludedCandidate(int index, String reason) {}
