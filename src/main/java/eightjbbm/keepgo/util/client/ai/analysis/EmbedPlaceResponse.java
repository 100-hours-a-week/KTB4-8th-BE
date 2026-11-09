package eightjbbm.keepgo.util.client.ai.analysis;

/// `placeId`는 요청이 배치여도 하나만 오므로 의미를 가정하지 않고 성공 판정에는 `success`만 쓴다.
public record EmbedPlaceResponse(Boolean success, Long placeId) {}
