package eightjbbm.keepgo.util.file;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageConfig {

    @Bean
    @ConditionalOnProperty(prefix = "keepgo.storage", name = "provider", havingValue = "local", matchIfMissing = true)
    public FileStorage localFileStorage(FileStorageProperties properties) {
        return new LocalFileStorage(properties.local().rootPath());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "keepgo.storage", name = "provider", havingValue = "s3")
    public S3Client s3Client(FileStorageProperties properties) {
        return S3Client.builder().region(Region.of(properties.s3().region())).build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "keepgo.storage", name = "provider", havingValue = "s3")
    public FileStorage s3FileStorage(S3Client s3Client, FileStorageProperties properties) {
        return new S3FileStorage(s3Client, properties.s3().bucket());
    }
}
