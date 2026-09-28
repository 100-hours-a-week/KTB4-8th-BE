package eightjbbm.keepgo.util.client.geocoding;

import eightjbbm.keepgo.util.dto.MapCoordinatesToLocationNameResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(GeoCodingApiClientProperties.class)
public class GeoCodingApiClient {

    private final String path = "/req/address";
    @Qualifier("geoCodingRestClient")
    private final RestClient geoCodingRestClient;
    private final GeoCodingApiClientProperties properties;

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
                                    .queryParam("key", properties.key());
                            return builder.build();
                        }
                )
                .retrieve()
                .body(MapCoordinatesToLocationNameResponse.class);
    }
}
