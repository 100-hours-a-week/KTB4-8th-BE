package eightjbbm.keepgo.util;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;

public class FileUtil {

    public static String extractPathFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }
            URI uri = new URI(url);
            String path = uri.getPath();
            return path != null ? path : url;
        } catch (URISyntaxException e) {
            return url;
        }
    }

    public static String toFullUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }

        if (relativePath.startsWith("http")) {
            return relativePath;
        }

        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .build()
                .toUriString();

        return baseUrl + relativePath;
    }
}
