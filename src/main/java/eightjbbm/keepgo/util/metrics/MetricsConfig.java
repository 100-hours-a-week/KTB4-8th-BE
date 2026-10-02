package eightjbbm.keepgo.util.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationConvention;

import java.util.List;

/// 앱 상세 메트릭 계약 v1 설정
@Configuration
public class MetricsConfig {

    private static final String CONTRACT_VERSION = "1";

    /// 실제로 노출하는 capability만 광고한다.
    private static final List<String> CAPABILITIES = List.of(
            "http",
            "jvm",
            "db_pool"
    );

    @Bean
    ServerRequestObservationConvention trafficClassServerRequestObservationConvention() {
        return new TrafficClassServerRequestObservationConvention();
    }

    /// keepgo_observability_info{contract="1",capability="..."} 1
    @Bean
    MeterBinder observabilityInfo() {
        return registry -> CAPABILITIES.forEach(capability ->
                Gauge.builder("keepgo.observability.info", () -> 1)
                        .description("Metrics contract capabilities exposed by this application")
                        .tag("contract", CONTRACT_VERSION)
                        .tag("capability", capability)
                        .register(registry)
        );
    }
}
