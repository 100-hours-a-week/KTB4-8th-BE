package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.recommendation.GetHotPlaceListResponse;
import eightjbbm.keepgo.recommendation.GetOngoingEventListResponse;
import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.sandbox.OutingGuideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class HotPlaceServiceV2 {

    private final OutingGuideRepository outingGuideRepository;

    public GetHotPlaceListResponse getHotPlaceList() {
        return new GetHotPlaceListResponse(
                outingGuideRepository.findAll().stream()
                        .filter(outingGuide -> outingGuide instanceof OutingPlace)
                        .map(outingGuide ->
                                new GetHotPlaceListResponse.Place(
                                        outingGuide.getName(),
                                        outingGuide.getAddress().toRoad()))
                        .toList());
    }

    public GetOngoingEventListResponse getOngoingEventList() {
        return new GetOngoingEventListResponse(
                outingGuideRepository.findAll().stream()
                        .filter(outingGuide -> outingGuide instanceof OutingEvent)
                        .filter(outingGuide -> outingGuide.isAvailable(Instant.now()))
                        .map(outingGuide ->
                                new GetOngoingEventListResponse.Event(
                                        outingGuide.getName(),
                                        outingGuide.getAddress().toRoad(),
                                        ((OutingEvent) outingGuide).getStartDatetime() == null
                                                ? null
                                                : ((OutingEvent) outingGuide).getStartDatetime().atZone(ZoneId.of("Asia/Seoul")).toLocalDate(),
                                        ((OutingEvent) outingGuide).getEndDatetime() == null
                                                ? null
                                                : ((OutingEvent) outingGuide).getEndDatetime().atZone(ZoneId.of("Asia/Seoul")).toLocalDate()
                                ))
                        .toList());
    }
}
