package eightjbbm.keepgo.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eightjbbm.keepgo.member.dto.UpdateMemberInfoCommand;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.util.file.File;
import eightjbbm.keepgo.util.file.FileRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("회원 정보 수정 서비스")
class MemberServiceTest {
    private static final long MEMBER_ID = 7L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private FileRepository fileRepository;

    @InjectMocks
    private MemberService memberService;

    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.create("기존닉네임");
        member.updateProfileImage(new File("/public/files/old"));
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    }

    @Test
    @DisplayName("닉네임이 없으면 기존 닉네임을 유지한다")
    void keepsNicknameWhenNull() {
        memberService.updateMemberInfo(new UpdateMemberInfoCommand(MEMBER_ID, null, null));

        assertThat(member.getNickname()).isEqualTo("기존닉네임");
    }

    @Test
    @DisplayName("프로필 사진 주소가 없으면 파일을 조회하지 않고 기존 사진을 유지한다")
    void keepsProfileImageWithoutLookupWhenNull() {
        memberService.updateMemberInfo(new UpdateMemberInfoCommand(MEMBER_ID, "새닉네임", null));

        assertThat(member.getNickname()).isEqualTo("새닉네임");
        assertThat(member.getProfileImage().getStoragePath()).isEqualTo("/public/files/old");
        verify(fileRepository, never()).findByStoragePath(null);
    }

    @Test
    @DisplayName("프로필 사진 주소가 있으면 해당 파일로 교체한다")
    void changesProfileImageWhenUrlGiven() {
        var newFile = new File("/public/files/new");
        when(fileRepository.findByStoragePath("/public/files/new")).thenReturn(Optional.of(newFile));

        memberService.updateMemberInfo(
                new UpdateMemberInfoCommand(MEMBER_ID, "새닉네임", "http://localhost/public/files/new"));

        assertThat(member.getProfileImage()).isSameAs(newFile);
    }
}
