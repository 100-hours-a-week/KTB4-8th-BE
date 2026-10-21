package eightjbbm.keepgo.util.file;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LocalFileStorage implements FileStorage {

    private final Path rootPath;

    public LocalFileStorage(String rootPath) {
        this.rootPath = Paths.get(rootPath)
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public void store(MultipartFile file, String key) throws IOException {
        Path destination = resolvePath(key);

        Files.createDirectories(destination.getParent());
        file.transferTo(destination.toFile());
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolvePath(key));
    }

    private Path resolvePath(String key) {
        Path destination = rootPath.resolve(key).normalize();

        if (!destination.startsWith(rootPath)
                || destination.equals(rootPath)) {
            throw new IllegalArgumentException("INVALID_FILE_STORAGE_KEY");
        }

        return destination;
    }
}