package eightjbbm.keepgo.util.file;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/public/files")
@RequiredArgsConstructor
public class FileAccessController {

    private final FileAccessService fileAccessService;

    @GetMapping("/{identifier}")
    public ResponseEntity<Void> getFile(
            @PathVariable UUID identifier
    ) {
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(fileAccessService.resolve(identifier.toString()))
                .cacheControl(CacheControl.noStore())
                .build();
    }
}