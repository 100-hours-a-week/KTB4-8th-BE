package eightjbbm.keepgo.recommendation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import eightjbbm.keepgo.recommendation.service.RecommendationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("V1 장소 목록 지원 종료")
class PlaceListControllerTest {
    @Test
    @DisplayName("V1 장소 목록은 인증 필터 없이도 컨트롤러에 매핑되지 않는다")
    void retiresV1PlaceList() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(
                        new RecommendationController(mock(RecommendationService.class), new StaticMessageSource()))
                .build();
        var result = mvc.perform(get("/api/v1/places")
                        .param("sort", "trending")
                        .param("size", "10")
                        .param("cursor", "0"))
                .andReturn();
        assertThat(result.getHandler()).isNull();
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }
}
