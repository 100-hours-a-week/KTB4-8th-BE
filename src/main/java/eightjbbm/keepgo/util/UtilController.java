package eightjbbm.keepgo.util;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UtilController {

    @PostMapping()
    public void uploadFile() {
        /*
        파일 업로드 API
        요청 본문: 파일(아마 Multipart)
        응답 본문: 파일 주소
        이건 교재를 좀 더 봐야할 듯.
         */
    }

    @GetMapping
    public void readFile() {
        /*
        파일 조회 API
        요청 본문: 파일 주소?
        응답 본문: 파일
        이것도 교재를 좀 더 보기.
         */
    }

    @GetMapping
    public void searchAddressByKeyword() {
        /*
        지역 검색 API
        요청 본문: 검색 키워드
        1. 지역 검색 API를 호출하여 키워드 검색 진행
        2. 결과 반환
         */
    }
}
