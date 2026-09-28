package eightjbbm.keepgo.util.dto.stub;

import java.util.List;

public record AdvertisementListResponse(
        List<Advertisement> advertisements
) {
    record Advertisement(
       String attachedImageUrl,
       String title,
       String description
    ) {}

    public static AdvertisementListResponse stub() {
        return new AdvertisementListResponse(
                List.of(
                        new Advertisement(
                                "/public/attachment/hangang_festival.jpg",
                                "한강의 밤을 밝히는 빛의 축제",
                                "빛과 미디어아트로 새롭게 만나는 서울의 가을밤"
                        ),
                        new Advertisement(
                                "/public/attachment/kes2026.jpg",
                                "미래 기술을 먼저 만나다",
                                "AI·로봇·모빌리티가 한자리에 모이는 KES 2026."
                        ),
                        new Advertisement(
                                "/public/attachment/busan_rock_festival.jpg",
                                "가을을 뒤흔들 록의 함성",
                                "국내외 아티스트의 강렬한 라이브 무대를 만나는 2026 부산국제록페스티벌"
                        )
                )
        );
    }
}
