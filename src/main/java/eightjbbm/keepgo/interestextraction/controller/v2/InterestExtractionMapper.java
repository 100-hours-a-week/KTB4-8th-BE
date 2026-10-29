package eightjbbm.keepgo.interestextraction.controller.v2;

import eightjbbm.keepgo.interestextraction.service.GetConnectedServiceListResult;
import eightjbbm.keepgo.interestextraction.service.GetYoutubeConnectionResult;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface InterestExtractionMapper {

    InterestExtractionMapper interestExtractionMapper = Mappers.getMapper(InterestExtractionMapper.class);

    GetConnectedServiceListResponse fromResult(GetConnectedServiceListResult result);

    GetYoutubeConnectionResponse fromResult(GetYoutubeConnectionResult result);
}
