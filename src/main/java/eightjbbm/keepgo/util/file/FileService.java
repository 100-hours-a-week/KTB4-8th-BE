package eightjbbm.keepgo.util.file;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {
    private final FileRepository fileRepository;
    private final FileStorage fileStorage;
    private final FileStorageProperties storageProperties;

    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif");

    @Transactional(rollbackFor = Exception.class)
    public String uploadProfileImage(MultipartFile file, Long userId) throws FileUploadException {
        return uploadFile(file, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public String uploadFile(MultipartFile file, Long userId) throws FileUploadException {
        String extension = extractAndValidateExtension(file);
        String filename = generateFilename("profile", extension);
        String storageKey = "profile/" + filename;

        try {
            fileStorage.store(file, storageKey);
        } catch (IOException e) {
            throw new RuntimeException("INTERNAL_SERVER_ERROR", e);
        }

        registerRollbackCleanup(storageKey);

        var provider = storageProperties.provider();
        boolean isS3 = provider == FileStorageProperties.Provider.S3;

        File storedFile = fileRepository.save(new File(
                provider,
                storageKey,
                isS3 ? storageProperties.s3().bucket() : null,
                isS3 ? storageProperties.s3().region() : null));

        return storedFile.getStoragePath();
    }

    private String extractAndValidateExtension(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new RuntimeException("FILE_NAME_REQUIRED");
        }

        String extension = StringUtils.getFilenameExtension(originalName);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new RuntimeException("INVALID_FILE_EXTENSION"); // 415?
        }

        return extension;
    }

    private String generateFilename(String prefix, String extension) {
        String timestamp =
                Instant.now().atZone(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return prefix + "-" + timestamp + "-" + uuid + "." + extension;
    }

    private void registerRollbackCleanup(String storageKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteAfterRollback(storageKey);
                } else if (status == STATUS_UNKNOWN) {
                    log.error(
                            "파일 메타데이터 트랜잭션 결과를 확인할 수 없습니다. " + "파일 상태 확인이 필요합니다. provider={}, key={}",
                            storageProperties.provider(),
                            storageKey);
                }
            }
        });
    }

    private void deleteAfterRollback(String storageKey) {
        try {
            fileStorage.delete(storageKey);
        } catch (Exception e) {
            log.error("DB 롤백 후 파일 삭제에 실패했습니다. provider={}, key={}", storageProperties.provider(), storageKey, e);
        }
    }
}
