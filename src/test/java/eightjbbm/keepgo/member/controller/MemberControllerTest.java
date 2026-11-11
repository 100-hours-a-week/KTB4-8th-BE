package eightjbbm.keepgo.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import eightjbbm.keepgo.member.MemberMapper;
import eightjbbm.keepgo.member.dto.UpdateMemberInfoCommand;
import eightjbbm.keepgo.member.dto.UpdateMemberInfoResult;
import eightjbbm.keepgo.member.service.MemberService;
import eightjbbm.keepgo.util.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
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
                    .build()))
            .setControllerAdvice(new GlobalExceptionHandler())
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

    private static String nicknameJson(String nickname) {
        return "{\"nickname\":\"" + nickname + "\"}";
    }

    private static MockHttpServletRequestBuilder patchBody(String json) {
        return patch("/api/v1/user").contentType(MediaType.APPLICATION_JSON).content(json);
    }
}
