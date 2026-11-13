package eightjbbm.keepgo.util.client.ai.analysis;

/// 성공 판정에는 `success`만 쓰고 `placeId`는 판단에 사용하지 않는다.
public record EmbedPlaceResponse(Boolean success, Long placeId) {}
