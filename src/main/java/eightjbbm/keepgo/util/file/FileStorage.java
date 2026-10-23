package eightjbbm.keepgo.util.file;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorage {

    void store(MultipartFile file, String key) throws IOException;

    /**
     * 파일이 이미 없어도 성공으로 처리합니다.
     */
    void delete(String key) throws IOException;
}