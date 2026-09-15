package eightjbbm.keepgo.recommendation.controller;

import eightjbbm.keepgo.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping
    public void getOutingPlaceInfo() {

    }

    @GetMapping
    public void getOutingEventInfo() {

    }

    @GetMapping
    public void getOutingCourseInfo() {

    }

    @GetMapping
    public void getAdvertisementList() {

    }

    @GetMapping
    public void getAdvertisementInfo() {

    }

    @GetMapping
    public void getHotPlacesList() {

    }

    @PostMapping
    public void requestRecommendation() {

    }

    @GetMapping
    public void getRecommendedCourseInfo() {

    }

    @PostMapping
    public void stopRecommendation() {

    }
}
