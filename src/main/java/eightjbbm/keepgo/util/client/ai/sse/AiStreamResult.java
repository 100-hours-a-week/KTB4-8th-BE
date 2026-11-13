package eightjbbm.keepgo.util.client.ai.sse;

import tools.jackson.databind.JsonNode;

/// `done`으로 정상 종료한 스트림의 결과. `text`는 `delta` 텍스트를 순서대로 이어 붙인 값이다.
public record AiStreamResult(String text, JsonNode done) {}
