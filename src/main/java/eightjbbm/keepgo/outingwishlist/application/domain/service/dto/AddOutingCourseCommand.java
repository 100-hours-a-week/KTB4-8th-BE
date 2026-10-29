package eightjbbm.keepgo.outingwishlist.application.domain.service.dto;

import eightjbbm.keepgo.outingwishlist.adapter.in.web.dto.AddOutingCourseRequest;

public record AddOutingCourseCommand(Long memberId, Long outingCourseId) {
    public static AddOutingCourseCommand from(Long memberId, AddOutingCourseRequest request) {
        return new AddOutingCourseCommand(memberId, request.outingCourseId());
    }
}
