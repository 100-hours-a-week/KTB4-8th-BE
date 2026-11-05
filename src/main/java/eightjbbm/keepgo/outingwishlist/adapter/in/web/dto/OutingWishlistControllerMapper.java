package eightjbbm.keepgo.outingwishlist.adapter.in.web.dto;

import eightjbbm.keepgo.outingwishlist.application.domain.service.dto.*;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OutingWishlistControllerMapper {

    OutingWishlistControllerMapper INSTANCE = Mappers.getMapper(OutingWishlistControllerMapper.class);

    GetPlaceListResponse fromResult(GetPlaceListResult result);

    GetPlaceDetailResponse fromResult(GetPlaceDetailResult result);

    GetEventListResponse fromResult(GetEventListResult result);

    GetEventDetailResponse fromResult(GetEventDetailResult result);

    GetOutingCourseListResponse fromResult(GetOutingCourseListResult result);

    GetOutingCourseDetailResponse fromResult(GetOutingCourseDetailResult result);
}
