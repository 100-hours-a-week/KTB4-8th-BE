package eightjbbm.keepgo.util;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String rootPath = Paths.get(System.getProperty("user.dir"))
                .toAbsolutePath()
                .toString();

        String uploadPath = "file:///" + rootPath + "/uploads/";

        registry.addResourceHandler("/public/**")
                .addResourceLocations(uploadPath);
    }
}
