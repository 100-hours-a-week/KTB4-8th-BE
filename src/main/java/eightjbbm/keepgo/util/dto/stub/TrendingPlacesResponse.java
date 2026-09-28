package eightjbbm.keepgo.util.dto.stub;

import java.util.List;

public record TrendingPlacesResponse(
        List<HotPlace> hotPlaces
) {
    record HotPlace(
            String attachedImageUrl,
            String name,
            String location,
            String category
    ) {}

    public static TrendingPlacesResponse stub() {
        return new TrendingPlacesResponse(
                List.of(
                        new HotPlace(
                                "/public/attachment/around_sungsu.jpg",
                                "어라운드 성수",
                                "서울 성동구",
                                "카페"
                        ),
                        new HotPlace(
                                "/public/attachment/sansugapsan.jpg",
                                "을지로 산수갑산",
                                "서울 중구",
                                "맛집"
                        ),
                        new HotPlace(
                                "/public/attachment/boan_yeogwan.jpg",
                                "보안여관 전시",
                                "서울 종로구",
                                "전시"
                        )
                )
        );
    }
}
