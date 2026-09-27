package eightjbbm.keepgo.util.client;

import eightjbbm.keepgo.util.dto.MapCoordinatesToLocationNameResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
@RequiredArgsConstructor
public class GeoCodingClient {

    private final String path = "/req/address";
    @Qualifier("geoCodingRestClient")
    private final RestClient geoCodingRestClient;
    private final String apiKey = "placeholder";

    public MapCoordinatesToLocationNameResponse mapCoordinatesToLocationName(Float lat, Float lng) {
        return geoCodingRestClient.get()
                .uri(
                        uriBuilder -> {
                            UriBuilder builder = uriBuilder
                                    .path(path)
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
