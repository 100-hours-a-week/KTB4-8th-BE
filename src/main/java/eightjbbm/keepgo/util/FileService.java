package eightjbbm.keepgo.util;

import lombok.RequiredArgsConstructor;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {
    private final FileRepository fileRepository;

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final Path PROFILE_DIR = PROJECT_ROOT.resolve("uploads/profile");
    private static final String PROFILE_URL = "/public/profile/";
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif");

    public String uploadProfileImage(MultipartFile file, Long userId) throws FileUploadException {
        return uploadFile(file, userId);
    }

    public String uploadFile(MultipartFile file, Long userId) throws FileUploadException {
        String extension = extractAndValidateExtension(file);
        String filename = generateFilename("profile", extension);
        Path savePath = FileService.PROFILE_DIR.resolve(filename);

        try {
            if (!Files.exists(FileService.PROFILE_DIR)) {
                Files.createDirectories(FileService.PROFILE_DIR);
            }
            file.transferTo(savePath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("INTERNAL_SERVER_ERROR");
        }

        String dbFilePath = FileService.PROFILE_URL + filename;
        fileRepository.save(new File(dbFilePath));
        return dbFilePath;
    }

    private String extractAndValidateExtension(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new RuntimeException("FILE_NAME_REQUIRED");
        }

        String extension = StringUtils.getFilenameExtension(originalName);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new RuntimeException("INVALID_FILE_EXTENSION"); //415?
        }

        return extension;
    }

    private String generateFilename(String prefix, String extension) {
        String timestamp = Instant.now().atZone(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID()
                .toString()
                .substring(0, 8);
        return prefix + "-" + timestamp + "-" + uuid + "." + extension;
    }
}
