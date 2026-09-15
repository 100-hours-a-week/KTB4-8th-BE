package eightjbbm.keepgo.member.service;

import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.dto.getUserInfoResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;

    public getUserInfoResponseDto getUserInfo(Long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow();
        return new getUserInfoResponseDto(member.getNickname(), member.getProfileImage().getStoragePath());
    }
}
