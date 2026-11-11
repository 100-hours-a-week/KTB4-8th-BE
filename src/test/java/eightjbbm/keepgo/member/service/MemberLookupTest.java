package eightjbbm.keepgo.member.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import eightjbbm.keepgo.member.dto.*;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.exception.MemberNotFoundException;
import eightjbbm.keepgo.member.repository.*;
import eightjbbm.keepgo.util.file.FileRepository;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
@DisplayName("회원 조회 실패")
class MemberLookupTest {
    @Mock
    MemberRepository members;

    @Mock
    OAuthAccountRepository accounts;

    @Mock
    FileRepository files;

    @InjectMocks
    MemberService service;

    @Test
    @DisplayName("정보 수정 중 회원이 없으면 404를 반환한다")
    void updateMissingMember() {
        assertThatThrownBy(() -> service.updateMemberInfo(new UpdateMemberInfoCommand(1L, null, null)))
                .isInstanceOfSatisfying(MemberNotFoundException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getBody().getProperties()).containsEntry("code", "MEMBER_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("정보 조회 중 회원이 없으면 404를 반환한다")
    void getMissingMember() {
        assertThatThrownBy(() -> service.getMemberInfo(new GetMemberInfoCommand(1L)))
                .isInstanceOfSatisfying(MemberNotFoundException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getBody().getProperties()).containsEntry("code", "MEMBER_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("OAuth 계정이 없으면 내부 불변식 위반이다")
    void missingOAuthAccount() {
        Member member = Member.create("member");
        when(members.findById(1L)).thenReturn(Optional.of(member));
        assertThatThrownBy(() -> service.getMemberInfo(new GetMemberInfoCommand(1L)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("회원 정보 조회는 읽기 전용 트랜잭션이다")
    void readOnlyTransaction() throws Exception {
        assertThat(MemberService.class
                        .getMethod("getMemberInfo", GetMemberInfoCommand.class)
                        .getAnnotation(Transactional.class)
                        .readOnly())
                .isTrue();
    }
}
