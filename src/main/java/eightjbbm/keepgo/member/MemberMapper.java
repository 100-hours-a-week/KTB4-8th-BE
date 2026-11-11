package eightjbbm.keepgo.member;

import eightjbbm.keepgo.member.dto.GetMemberInfoCommand;
import eightjbbm.keepgo.member.dto.UpdateMemberInfoCommand;
import eightjbbm.keepgo.member.dto.UpdateMemberInfoRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MemberMapper {

    GetMemberInfoCommand toGetMemberInfoCommand(Long memberId);

    @Mapping(source = "request.profileImageUrl", target = "profileImagePath")
    UpdateMemberInfoCommand toUpdateMemberInfoCommand(Long memberId, UpdateMemberInfoRequest request);
}
