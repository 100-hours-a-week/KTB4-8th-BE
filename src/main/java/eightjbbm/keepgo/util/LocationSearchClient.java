package eightjbbm.keepgo.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
@RequiredArgsConstructor
public class LocationSearchClient {

    private String baseUrl = "https://business.juso.go.kr/addrlink/addrLinkApi.do";
    private String confmKey = "";

    private final RestClient locationSearchRestClient;

    /// 키워드 기반 지역 검색 래퍼
    ///
    /// 유효성 검사를 진행한 후 private 메소드 호출
    ///
    /// 진행해야 하는 유효성 검사 목록:
    public void searchByKeyword(String keyword) {
        searchByKeywordApi(keyword);
    }

    private String searchByKeywordApi(String keyword) {
        return locationSearchRestClient.get()
                .uri(
                        uriBuilder -> {
                            UriBuilder builder = uriBuilder
                                    .path(baseUrl)
                                    .queryParam("confmKey", confmKey)
                                    .queryParam("currentPage", 0)
                                    .queryParam("countPerPage", 0)
                                    .queryParam("keyword", keyword)
                                    .queryParam("resultType", "json");
                            return builder.build();
                        })
                .retrieve()
                .body(String.class);
    }
}
