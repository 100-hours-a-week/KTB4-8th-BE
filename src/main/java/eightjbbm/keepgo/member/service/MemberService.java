package eightjbbm.keepgo.member.service;

import eightjbbm.keepgo.member.dto.*;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.util.AiServerClient;
import eightjbbm.keepgo.util.File;
import eightjbbm.keepgo.util.FileRepository;
import eightjbbm.keepgo.util.YoutubeApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final FileRepository fileRepository;
    private final YoutubeApiClient youtubeApiClient;
    private final AiServerClient aiServerClient;

    public UpdateMemberInfoResult updateMemberInfo(
            UpdateMemberInfoCommand command
    ) {
        /*
        회원 정보 수정 API
        요청 본문: 변경할 회원 닉네임, 변경할 회원 프로필 사진 주소
        1. 요청 본문을 바탕으로 회원의 닉네임과 프로필 사진 수정
        2. 변경된 닉네임과 변경된 회원 프로필 사진 주소 반환
        */
        Member member = memberRepository.findById(command.userId()).orElseThrow();
        member.updateNickname(command.nickname());
        File profileImage = fileRepository.findByStoragePath(command.profileImagePath()).orElseThrow();
        member.updateProfileImage(profileImage);
        return UpdateMemberInfoResult.from(member);
    }

    public GetMemberInfoResult getMemberInfo(
            GetMemberInfoCommand command
    ) {
        /*
        회원 정보 조회 API (구현 완료)
        1. authentication에서 회원 정보 추출
        2. 회원의 닉네임, 프로필 사진 주소, 이메일 주소 반환
        */
        Member member = memberRepository.findById(command.userId()).orElseThrow();
        return GetMemberInfoResult.from(member);
    }

    public SynchronizeYoutubeLikeVideosResult synchronizeYoutubeLikeVideos(
            SynchronizeYoutubeLikeVideosCommand command
    ) {
        /*
        좋아요한 동영상 목록 동기화 API
        1. authentication에서 회원 정보 추출
        2. 추출한 정보를 바탕으로 해당 회원이 좋아요를 누른 동영상의 목록을 유튜브 API로 조회
        3. 조회된 목록을 바탕으로 AI 서버에게 분석 요청
        4. 모든 영상 분석 완료 시 완료 응답 반환
        */
        List<String> urls = youtubeApiClient.retrieveLikedVideos(command.userId());
        for (String url: urls) {
            aiServerClient.analyzeVideo(url);
        }

        return null;
    }
}
