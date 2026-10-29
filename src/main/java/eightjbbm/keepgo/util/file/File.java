package eightjbbm.keepgo.util.file;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class File {

    @Id
    @GeneratedValue
    private Long id;

    private String storagePath;

    @Enumerated(EnumType.STRING)
    private FileStorageProperties.Provider storageType;

    private String objectKey;

    private String bucket;

    private String region;

    // 기존 코드 및 기존 로컬 데이터와의 호환용
    public File(String storagePath) {
        this.storagePath = storagePath;
    }

    public File(FileStorageProperties.Provider storageType, String objectKey, String bucket, String region) {
        this.storagePath = "/public/files/" + UUID.randomUUID();
        this.storageType = storageType;
        this.objectKey = objectKey;
        this.bucket = bucket;
        this.region = region;
    }
}
