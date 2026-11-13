package eightjbbm.keepgo.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import eightjbbm.keepgo.member.MemberMapper;
import eightjbbm.keepgo.member.dto.UpdateMemberInfoCommand;
import eightjbbm.keepgo.member.dto.UpdateMemberInfoResult;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.exception.MemberNotFoundException;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.member.repository.OAuthAccountRepository;
import eightjbbm.keepgo.member.service.MemberService;
import eightjbbm.keepgo.util.exception.GlobalExceptionHandler;
import eightjbbm.keepgo.util.file.FileRepository;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("회원 정보 수정 요청 검증")
class MemberControllerTest {
    private final MemberService memberService = mock(MemberService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new MemberController(memberService, Mappers.getMapper(MemberMapper.class)))
            .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .addMixIn(
                            org.springframework.http.ProblemDetail.class,
                            org.springframework.http.converter.json.ProblemDetailJacksonMixin.class)
                    .build()))
            .setControllerAdvice(new GlobalExceptionHandler(messageSource()))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

    @BeforeEach
    void authenticate() {
        var jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("7")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(memberService.updateMemberInfo(any())).thenReturn(new UpdateMemberInfoResult("홍길동", ""));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"홍길동", "ㅋㅋ", "keepgo1", "가a1ㅏ"})
    @DisplayName("한글 자모 영문 숫자 닉네임은 수정된다")
    void acceptsKoreanEnglishDigitNickname(String nickname) throws Exception {
        mvc.perform(patchBody(nicknameJson(nickname))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"   ", "!@#$", "<script>alert(1)</script>", "12345678901"})
    @DisplayName("허용되지 않는 닉네임은 서비스 호출 전에 거부된다")
    void rejectsInvalidNickname(String nickname) throws Exception {
        mvc.perform(patchBody(nicknameJson(nickname))).andExpect(status().isUnprocessableContent());
        verify(memberService, never()).updateMemberInfo(any());
    }

    @Test
    @DisplayName("300자 닉네임은 500이 아니라 검증 오류로 거부된다")
    void rejectsTooLongNickname() throws Exception {
        mvc.perform(patchBody(nicknameJson("B".repeat(300)))).andExpect(status().isUnprocessableContent());
        verify(memberService, never()).updateMemberInfo(any());
    }

    @Test
    @DisplayName("profile_image_url 없이 닉네임만 보내면 사진 변경 없이 수정된다")
    void acceptsNicknameOnly() throws Exception {
        mvc.perform(patchBody(nicknameJson("홍길동"))).andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(UpdateMemberInfoCommand.class);
        verify(memberService).updateMemberInfo(captor.capture());
        assertThat(captor.getValue().profileImagePath()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\u3000"})
    @DisplayName("profile_image_url 값이 비어 있으면 거부된다")
    void rejectsBlankProfileImageUrl(String url) throws Exception {
        mvc.perform(patchBody("{\"nickname\":\"홍길동\",\"profile_image_url\":\"" + url + "\"}"))
                .andExpect(status().isUnprocessableContent());
        verify(memberService, never()).updateMemberInfo(any());
    }

    @Test
    @DisplayName("회원이 없으면 조회와 수정에서 404 오류 계약을 반환한다")
    void missingMemberReturnsProblem() throws Exception {
        var members = mock(MemberRepository.class);
        MockMvc actualMvc = actualServiceMvc(members, mock(OAuthAccountRepository.class));
        actualMvc
                .perform(get("/api/v1/user"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
                .andExpect(jsonPath("$.type").value("/problems/member-not-found"))
                .andExpect(jsonPath("$.title").value("회원을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.instance").value("/api/v1/user"));
        actualMvc
                .perform(patchBody(nicknameJson("홍길동")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
                .andExpect(jsonPath("$.type").value("/problems/member-not-found"))
                .andExpect(jsonPath("$.title").value("회원을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.instance").value("/api/v1/user"));
    }

    @Test
    @DisplayName("OAuth 계정이 없으면 내부 메시지를 숨긴 500을 반환한다")
    void missingOAuthReturnsSafeProblem() throws Exception {
        var members = mock(MemberRepository.class);
        when(members.findById(7L)).thenReturn(Optional.of(Member.create("secret-member-7")));
        MockMvc actualMvc = actualServiceMvc(members, mock(OAuthAccountRepository.class));
        actualMvc
                .perform(get("/api/v1/user"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("https://keepgo.kr/internal-server-error"))
                .andExpect(jsonPath("$.title").value("서버 내부 오류입니다."))
                .andExpect(jsonPath("$.instance").value("/api/v1/user"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("secret-member-7", "OAuth"));
    }

    @Test
    @DisplayName("회원 예외는 메시지 소스에서 type과 title을 조회한다")
    void memberProblemUsesMessageSource() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        var exception = new MemberNotFoundException();
        assertThat(exception.getTypeMessageCode()).isEqualTo("problem.MEMBER_NOT_FOUND.type");
        assertThat(exception.getTitleMessageCode()).isEqualTo("problem.MEMBER_NOT_FOUND.title");
        var body = exception.updateAndGetBody(source, Locale.KOREA);
        assertThat(body.getType()).hasToString("/problems/member-not-found");
        assertThat(body.getTitle()).isEqualTo("회원을 찾을 수 없습니다.");
    }

    private static MockMvc actualServiceMvc(MemberRepository members, OAuthAccountRepository accounts) {
        var service = new MemberService(members, mock(FileRepository.class), accounts);
        return MockMvcBuilders.standaloneSetup(new MemberController(service, Mappers.getMapper(MemberMapper.class)))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                        .addMixIn(
                                org.springframework.http.ProblemDetail.class,
                                org.springframework.http.converter.json.ProblemDetailJacksonMixin.class)
                        .build()))
                .setControllerAdvice(new GlobalExceptionHandler(messageSource()))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    private static ResourceBundleMessageSource messageSource() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }

    private static String nicknameJson(String nickname) {
        return "{\"nickname\":\"" + nickname + "\"}";
    }

    private static MockHttpServletRequestBuilder patchBody(String json) {
        return patch("/api/v1/user").contentType(MediaType.APPLICATION_JSON).content(json);
    }
}
