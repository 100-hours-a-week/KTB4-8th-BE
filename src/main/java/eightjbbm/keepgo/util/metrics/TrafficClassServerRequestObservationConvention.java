package eightjbbm.keepgo.util.metrics;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import java.util.Set;

/// http.server.requests에 traffic_class 태그를 추가하는 Observation Convention
///
/// - generation: 요청 안에서 AI 생성을 동기로 기다리는 API
/// - interactive: 그 외 전체
public class TrafficClassServerRequestObservationConvention extends DefaultServerRequestObservationConvention {

    static final String TRAFFIC_CLASS = "traffic_class";
    static final String GENERATION = "generation";
    static final String INTERACTIVE = "interactive";

    private static final Set<String> GENERATION_ROUTES = Set.of(
            "POST /api/v1/user/recommendation",
            "POST /api/v1/user/youtube-analyze"
    );

    @Override
    public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
        return super.getLowCardinalityKeyValues(context)
                .and(trafficClass(context));
    }

    protected KeyValue trafficClass(ServerRequestObservationContext context) {
        var request = context.getCarrier();
        var pathPattern = context.getPathPattern();

        if (request != null
                && pathPattern != null
                && GENERATION_ROUTES.contains(request.getMethod() + " " + pathPattern)) {
            return KeyValue.of(TRAFFIC_CLASS, GENERATION);
        }

        return KeyValue.of(TRAFFIC_CLASS, INTERACTIVE);
    }
}
