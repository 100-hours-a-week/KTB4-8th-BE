package eightjbbm.keepgo.util.file;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
public class FileUploadConfig implements WebMvcConfigurer {

    private final FileStorageProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String resourceLocation =
                Paths.get(properties.local().rootPath())
                        .toAbsolutePath()
                        .normalize()
                        .toUri()
                        .toString();

        if (!resourceLocation.endsWith("/")) {
            resourceLocation += "/";
        }

        registry.addResourceHandler("/public/**")
                .addResourceLocations(resourceLocation);
    }
}