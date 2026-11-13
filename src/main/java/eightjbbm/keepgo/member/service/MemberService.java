package eightjbbm.keepgo.member.service;

import eightjbbm.keepgo.member.dto.GetMemberInfoCommand;
import eightjbbm.keepgo.member.dto.GetMemberInfoResult;
import eightjbbm.keepgo.member.dto.UpdateMemberInfoCommand;
import eightjbbm.keepgo.member.dto.UpdateMemberInfoResult;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.entity.OAuthAccount;
import eightjbbm.keepgo.member.exception.MemberNotFoundException;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.member.repository.OAuthAccountRepository;
import eightjbbm.keepgo.util.file.FileRepository;
import eightjbbm.keepgo.util.file.FileUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final FileRepository fileRepository;
    private final OAuthAccountRepository oAuthAccountRepository;

    /// 회원 정보 수정 API
    /// @param command {@link UpdateMemberInfoCommand}
    /// @return {@link UpdateMemberInfoResult}
    @Transactional
    public UpdateMemberInfoResult updateMemberInfo(UpdateMemberInfoCommand command) {
        Member member = memberRepository.findById(command.memberId()).orElseThrow(MemberNotFoundException::new);
        if (command.nickname() != null) {
            member.updateNickname(command.nickname());
        }

        String profileImagePath = FileUtil.extractPathFromUrl(command.profileImagePath());
        if (profileImagePath != null) {
            fileRepository.findByStoragePath(profileImagePath).ifPresent(member::updateProfileImage);
        }
        return UpdateMemberInfoResult.from(member);
    }

    /// 회원 정보 조회 API
    ///
    /// @param command {@link GetMemberInfoCommand}
    /// @return {@link GetMemberInfoResult}
    @Transactional(readOnly = true)
    public GetMemberInfoResult getMemberInfo(GetMemberInfoCommand command) {
        Member member = memberRepository.findById(command.memberId()).orElseThrow(MemberNotFoundException::new);
        OAuthAccount oAuthAccount = oAuthAccountRepository
                .findByMember(member)
                .orElseThrow(() -> new IllegalStateException("회원의 OAuth 계정이 없습니다: " + command.memberId()));
        return GetMemberInfoResult.from(member, oAuthAccount);
    }
}
