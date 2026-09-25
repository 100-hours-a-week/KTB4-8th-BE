package eightjbbm.keepgo.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
@RequiredArgsConstructor
public class GeoCodingClient {

    private final String baseUrl = "https://api.vworld.kr/req/address";
    private final RestClient geoCodingRestClient;
    private final String apiKey = "placeholder";

    public MapCoordinatesToLocationNameResponse mapCoordinatesToLocationName(Float lat, Float lng) {
        return geoCodingRestClient.get()
                .uri(
                        uriBuilder -> {
                            UriBuilder builder = uriBuilder
                                    .host(baseUrl)
                                    .queryParam("service", "address")
                                    .queryParam("request", "getAddress")
                                    .queryParam("version", 2.0)
                                    .queryParam("crs", 4326)
                                    .queryParam("point", lat + "," + lng)
                                    .queryParam("format", "json")
                                    .queryParam("type", "ROAD")
                                    .queryParam("zipcode", false)
                                    .queryParam("simple", true)
                                    .queryParam("key", apiKey);
                            return builder.build();
                        }
                )
                .retrieve()
                .body(MapCoordinatesToLocationNameResponse.class);
    };
}
