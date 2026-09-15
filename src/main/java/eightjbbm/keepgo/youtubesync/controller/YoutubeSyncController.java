package eightjbbm.keepgo.youtubesync.controller;

import eightjbbm.keepgo.youtubesync.service.YoutubeSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class YoutubeSyncController {

    private final YoutubeSyncService youtubeSyncService;

    @PostMapping
    public void synchronizeYoutubeLikeVideos() {

    }
}
