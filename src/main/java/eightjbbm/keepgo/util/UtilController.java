package eightjbbm.keepgo.util;

import eightjbbm.keepgo.util.dto.SearchAddressByKeywordResponse;
import lombok.RequiredArgsConstructor;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RequestMapping("/api/v1")
@RestController
@RequiredArgsConstructor
public class UtilController {

    private final LocationSearchClient locationSearchClient;
    private final FileService fileService;

     /// 프로필 사진 업로드 API
     ///
     /// 구현 1차적으로 완료
     /// @param jwt
     /// @param file
    @PostMapping("/user/profile-image")
    public ResponseEntity<Void> uploadProfileImage(@AuthenticationPrincipal Jwt jwt, @RequestPart("profileImage") MultipartFile file) throws FileUploadException {

        Long userId = Long.valueOf(jwt.getSubject());
        String uploadPath = fileService.uploadProfileImage(file, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Location", uploadPath)
                .build();
    }

    /// 지역 검색 API
    ///
    /// (현재 프론트에게 기능 위임 논의 중)
    /// @param query 검색할 키워드
    /// @return {@link SearchAddressByKeywordResponse}
    @GetMapping("/address")
    public ResponseEntity<Void> searchAddressByKeyword(@RequestParam String query) {

        locationSearchClient.searchByKeyword(query);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }
}
