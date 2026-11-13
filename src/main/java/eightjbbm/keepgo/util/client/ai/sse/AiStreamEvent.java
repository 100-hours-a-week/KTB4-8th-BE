package eightjbbm.keepgo.util.client.ai.sse;

import tools.jackson.databind.JsonNode;

/// 스트림 도중 도착한 `delta`·`status` 이벤트. `data`는 이벤트 본문이다.
public record AiStreamEvent(String name, JsonNode data) {}
