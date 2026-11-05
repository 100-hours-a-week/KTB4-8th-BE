package eightjbbm.keepgo.util.file;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class FileAccessService {

    private final FileRepository fileRepository;

    private final Map<String, S3Presigner> presigners = new ConcurrentHashMap<>();

    public URI resolve(String identifier) {
        File file = fileRepository
                .findByStoragePath("/public/files/" + identifier)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."));

        if (file.getStorageType() == FileStorageProperties.Provider.LOCAL) {
            return URI.create("/public/" + file.getObjectKey());
        }

        if (file.getStorageType() == FileStorageProperties.Provider.S3) {
            return createS3Url(file);
        }

        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일의 저장소 정보가 올바르지 않습니다.");
    }

    private URI createS3Url(File file) {
        S3Presigner presigner = presigners.computeIfAbsent(
                file.getRegion(),
                region -> S3Presigner.builder().region(Region.of(region)).build());

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(file.getBucket())
                .key(file.getObjectKey())
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(getObjectRequest)
                .build();

        return URI.create(presigner.presignGetObject(presignRequest).url().toExternalForm());
    }

    @PreDestroy
    public void close() {
        presigners.values().forEach(S3Presigner::close);
    }
}
