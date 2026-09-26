package eightjbbm.keepgo.member;

import eightjbbm.keepgo.member.dto.GetMemberInfoCommand;
import eightjbbm.keepgo.member.dto.SynchronizeYoutubeLikeVideosCommand;
import eightjbbm.keepgo.member.dto.UpdateMemberInfoCommand;
import eightjbbm.keepgo.member.dto.UpdateMemberInfoRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface MemberMapper {

    MemberMapper INSTANCE = Mappers.getMapper(MemberMapper.class);

    GetMemberInfoCommand toGetMemberInfoCommand(Long memberId);

    SynchronizeYoutubeLikeVideosCommand toSynchronizeYoutubeLikeVideosCommand(Long memberId);

    @Mapping(source = "profileImageUrl", target = "profileImagePath")
    UpdateMemberInfoCommand toUpdateMemberInfoCommand(Long memberId, UpdateMemberInfoRequest request);
}
