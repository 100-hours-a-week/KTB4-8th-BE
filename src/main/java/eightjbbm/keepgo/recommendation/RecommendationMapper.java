package eightjbbm.keepgo.recommendation;

import eightjbbm.keepgo.recommendation.dto.RequestRecommendationCommand;
import eightjbbm.keepgo.recommendation.dto.StopRecommendationCommand;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface RecommendationMapper {

    RecommendationMapper INSTANCE = Mappers.getMapper(RecommendationMapper.class);

    RequestRecommendationCommand toRequestRecommendationCommand(Long memberId);

    StopRecommendationCommand toStopRecommendationCommand(Long memberId);
}
