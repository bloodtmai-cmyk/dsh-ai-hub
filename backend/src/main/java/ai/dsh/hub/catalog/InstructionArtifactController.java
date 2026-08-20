package ai.dsh.hub.catalog;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
public class InstructionArtifactController {
    private final InstructionArtifactService service;

    public InstructionArtifactController(InstructionArtifactService service) {
        this.service = service;
    }

    @PostMapping(path = "/api/admin/instructions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Capability upload(@RequestParam MultipartFile file,
                             @RequestParam String instructionId,
                             @RequestParam String displayName,
                             @RequestParam String description,
                             @RequestParam String releaseVersion,
                             Authentication authentication) throws IOException {
        return service.upload(file.getOriginalFilename(), file.getBytes(), instructionId, displayName, description,
                releaseVersion, authentication.getName());
    }

    @GetMapping("/api/admin/instructions/{id}/artifact")
    public ResponseEntity<byte[]> adminDownload(@PathVariable UUID id) {
        return response(service.adminDownload(id));
    }

    @GetMapping("/api/client/instructions/{id}/artifact")
    public ResponseEntity<byte[]> clientDownload(@PathVariable UUID id, Authentication authentication) {
        return response(service.download(id));
    }

    private static ResponseEntity<byte[]> response(InstructionArtifactService.Download download) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .eTag('"' + download.sha256() + '"')
                .body(download.content());
    }
}
