package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.util.Coordinate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class KakaoPlaceSearch {

    private static final String BASE_URL =
            "https://dapi.kakao.com/v2/local/search/keyword.json";

    private final String apiKey;
    private final HttpClient httpClient;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public KakaoPlaceSearch() {
        String key = System.getenv("KAKAO_REST_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "KAKAO_REST_API_KEY 환경변수가 설정되지 않았습니다."
            );
        }

        this.apiKey = key.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 검색 결과 첫 번째 장소의 좌표를 반환합니다.
     */
    public Coordinate getCoordinates(String keyword)
            throws IOException, InterruptedException {

        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("검색할 장소명을 입력해주세요.");
        }

        String encodedKeyword = URLEncoder.encode(
                keyword.trim(), StandardCharsets.UTF_8
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "?query=" + encodedKeyword + "&size=1"))
                .header("Authorization", "KakaoAK " + apiKey)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (response.statusCode() != 200) {
            throw new IOException(
                    "카카오 장소 검색 API 요청 실패: HTTP " + response.statusCode()
            );
        }

        JsonNode documents = objectMapper.readTree(response.body())
                .path("documents");

        if (!documents.isArray() || documents.isEmpty()) {
            throw new java.util.NoSuchElementException(
                    "'" + keyword + "'에 대한 검색 결과가 없습니다."
            );
        }

        JsonNode place = documents.get(0);

        try {
            float latitude = Float.parseFloat(place.path("y").asString());
            float longitude = Float.parseFloat(place.path("x").asString());
            return new Coordinate(latitude, longitude);
        } catch (NumberFormatException exception) {
            throw new IOException("API 응답의 좌표 형식이 올바르지 않습니다.", exception);
        }
    }
}