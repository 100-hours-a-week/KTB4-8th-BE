package eightjbbm.keepgo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import eightjbbm.keepgo.auth.accesstokenblacklist.AtBlacklistValidator;
import eightjbbm.keepgo.auth.exception.AccessTokenFailAuthenticationEntryPoint;
import eightjbbm.keepgo.recommendation.GetHotPlaceListResponse;
import eightjbbm.keepgo.recommendation.GetOngoingEventListResponse;
import eightjbbm.keepgo.recommendation.controller.RecommendationControllerV3;
import eightjbbm.keepgo.recommendation.service.HotPlaceServiceV2;
import eightjbbm.keepgo.util.file.FileController;
import eightjbbm.keepgo.util.file.FileService;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@SpringJUnitWebConfig(ApiFilterChainConfigTest.TestConfig.class)
@DisplayName("공개 추천 API 인증 정책")
class ApiFilterChainConfigTest {
    private final WebApplicationContext context;
    private MockMvc mvc;
    private HotPlaceServiceV2 hotPlaceService;

    @BeforeEach
    void setUp() {
        hotPlaceService = context.getBean(HotPlaceServiceV2.class);
        reset(hotPlaceService, context.getBean(FileService.class));
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("인증 없이 인기 장소를 기존 응답 구조로 조회한다")
    void getsHotPlacesAnonymously() throws Exception {
        when(hotPlaceService.getHotPlaceList())
                .thenReturn(new GetHotPlaceListResponse(List.of(new GetHotPlaceListResponse.Place("서울숲", "서울 성동구"))));

        MockHttpServletResponse response =
                mvc.perform(get("/api/v3/places/hot")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(new ObjectMapper().readTree(response.getContentAsString()))
                .isEqualTo(new ObjectMapper().readTree("{\"places\":[{\"name\":\"서울숲\",\"address\":\"서울 성동구\"}]}"));
    }

    @Test
    @DisplayName("잘못된 프로필 이미지 경로도 익명 접근을 차단한다")
    void rejectsObsoleteProfileImagePath() throws Exception {
        MockHttpServletResponse response =
                mvc.perform(post("/api/v1/users/me/profile-image")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("인증 없이 진행 중 행사를 기존 응답 구조로 조회한다")
    void getsOngoingEventsAnonymously() throws Exception {
        when(hotPlaceService.getOngoingEventList())
                .thenReturn(new GetOngoingEventListResponse(List.of(new GetOngoingEventListResponse.Event(
                        "가을 축제", "서울 중구", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))));

        MockHttpServletResponse response =
                mvc.perform(get("/api/v3/events/ongoing")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(new ObjectMapper().readTree(response.getContentAsString()))
                .isEqualTo(
                        new ObjectMapper()
                                .readTree(
                                        "{\"events\":[{\"name\":\"가을 축제\",\"address\":\"서울 중구\",\"start_date\":\"2026-11-01\",\"end_date\":\"2026-11-30\"}]}"));
    }

    @ParameterizedTest
    @CsvSource({
        "POST,/api/v3/places/hot", "PUT,/api/v3/places/hot", "PATCH,/api/v3/places/hot",
        "DELETE,/api/v3/places/hot", "HEAD,/api/v3/places/hot", "OPTIONS,/api/v3/places/hot",
        "POST,/api/v3/events/ongoing", "PUT,/api/v3/events/ongoing", "PATCH,/api/v3/events/ongoing",
        "DELETE,/api/v3/events/ongoing", "HEAD,/api/v3/events/ongoing", "OPTIONS,/api/v3/events/ongoing"
    })
    @DisplayName("공개 조회 경로도 GET 이외 메서드는 인증을 요구한다")
    void rejectsAnonymousNonGet(HttpMethod method, String path) throws Exception {
        MockHttpServletResponse response =
                mvc.perform(request(method, path)).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(hotPlaceService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/places", "/api/v1/user"})
    @DisplayName("V1 장소 목록과 회원 정보는 계속 인증을 요구한다")
    void rejectsProtectedEndpoints(String path) throws Exception {
        assertThat(mvc.perform(get(path)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v3/places/hot", "/api/v3/events/ongoing"})
    @DisplayName("공개 GET도 잘못된 Bearer 토큰은 거부한다")
    void rejectsInvalidBearerToken(String path) throws Exception {
        MockHttpServletResponse response = mvc.perform(
                        get(path).header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(hotPlaceService);
    }

    @Test
    @DisplayName("익명 프로필 이미지 업로드는 401이며 업로드를 수행하지 않는다")
    void rejectsAnonymousUpload() throws Exception {
        MockHttpServletResponse response = mvc.perform(
                        multipart("/api/v1/user/profile-image").file(profileImage()))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(context.getBean(FileService.class));
    }

    @Test
    @DisplayName("유효한 JWT로 프로필 이미지를 업로드하면 회원 식별자와 기존 응답을 유지한다")
    void uploadsWithValidJwt() throws Exception {
        FileService fileService = context.getBean(FileService.class);
        when(fileService.uploadProfileImage(any(), eq(7L))).thenReturn("/public/profile.png");
        String token = token("test-issuer");

        MockHttpServletResponse response = mvc.perform(multipart("/api/v1/user/profile-image")
                        .file(profileImage())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo("http://localhost/public/profile.png");
        verify(fileService).uploadProfileImage(any(), eq(7L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v3/places/hot", "/api/v3/events/ongoing"})
    @DisplayName("공개 GET도 서명이 유효하지만 발급자가 다른 토큰은 거부한다")
    void rejectsWrongIssuer(String path) throws Exception {
        MockHttpServletResponse response = mvc.perform(
                        get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("wrong-issuer")))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(hotPlaceService);
    }

    private String token(String issuer) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("7")
                .audience(List.of("test-audience"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .id("test-token")
                .claim("client_id", "test-client")
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).type("at+jwt").build();
        return context.getBean(JwtEncoder.class)
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    private MockMultipartFile profileImage() {
        return new MockMultipartFile("profileImage", "profile.png", "image/png", new byte[] {1, 2, 3});
    }

    @Configuration
    @EnableWebMvc
    @Import({ApiFilterChainConfig.class, RecommendationControllerV3.class, FileController.class})
    static class TestConfig implements WebMvcConfigurer {
        @Override
        public void configureMessageConverters(HttpMessageConverters.ServerBuilder converters) {
            converters.withJsonConverter(new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .build()));
        }

        @Bean
        HotPlaceServiceV2 hotPlaceService() {
            return mock(HotPlaceServiceV2.class);
        }

        @Bean
        FileService fileService() {
            return mock(FileService.class);
        }

        @Bean
        KeyPair keyPair() throws Exception {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }

        @Bean
        JwtDecoder jwtDecoder(KeyPair keyPair) {
            var properties = new JwtProperties(
                    Duration.ofMinutes(1), "test-key", "test-issuer", "test-audience", "test-client", null, null);
            var blacklistValidator = mock(AtBlacklistValidator.class);
            when(blacklistValidator.validate(any())).thenReturn(OAuth2TokenValidatorResult.success());
            return new SecurityConfig(properties).jwtDecoder((RSAPublicKey) keyPair.getPublic(), blacklistValidator);
        }

        @Bean
        JwtEncoder jwtEncoder(KeyPair keyPair) {
            var key = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey(keyPair.getPrivate())
                    .build();
            return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        }

        @Bean
        AccessTokenFailAuthenticationEntryPoint entryPoint() {
            return new AccessTokenFailAuthenticationEntryPoint(new ObjectMapper());
        }
    }
}
