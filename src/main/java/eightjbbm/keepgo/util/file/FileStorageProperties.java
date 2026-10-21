package eightjbbm.keepgo.util.file;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keepgo.storage")
public record FileStorageProperties(
        Provider provider,
        Local local,
        S3 s3
) {

    public FileStorageProperties {
        if (provider == null) {
            provider = Provider.LOCAL;
        }

        if (local == null) {
            local = new Local(null);
        }

        if (provider == Provider.S3) {
            if (s3 == null
                    || s3.bucket() == null
                    || s3.bucket().isBlank()) {
                throw new IllegalArgumentException(
                        "S3 저장소에는 keepgo.storage.s3.bucket 설정이 필요합니다."
                );
            }

            if (s3.region() == null || s3.region().isBlank()) {
                throw new IllegalArgumentException(
                        "S3 저장소에는 keepgo.storage.s3.region 설정이 필요합니다."
                );
            }
        }
    }

    public enum Provider {
        LOCAL,
        S3
    }

    public record Local(String rootPath) {

        public Local {
            if (rootPath == null || rootPath.isBlank()) {
                rootPath = "uploads";
            }
        }
    }

    public record S3(
            String bucket,
            String region
    ) {
    }
}