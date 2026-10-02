package eightjbbm.keepgo.util.file;

import lombok.RequiredArgsConstructor;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RequestMapping("/api/v1")
@RestController
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

     /// 프로필 사진 업로드 API
     ///
     /// 구현 1차적으로 완료
     /// @param jwt
     /// @param file
    @PostMapping("/user/profile-image")
    public ResponseEntity<Void> uploadProfileImage(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("profileImage") MultipartFile file
    ) throws FileUploadException {

        Long userId = Long.valueOf(jwt.getSubject());
        String uploadPath = fileService.uploadProfileImage(file, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Location", toFullUrl(uploadPath))
                .build();
    }

    public static String toFullUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        // 이미 http로 시작하면 변환 없이 반환
        if (relativePath.startsWith("http")) {
            return relativePath;
        }

        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .build()
                .toUriString();

        return baseUrl + relativePath;
    }
}
