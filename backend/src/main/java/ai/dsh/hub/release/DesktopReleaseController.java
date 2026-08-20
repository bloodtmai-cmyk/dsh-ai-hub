package ai.dsh.hub.release;

import ai.dsh.hub.common.PageResponse;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
public class DesktopReleaseController {
    private final DesktopReleaseService service;

    public DesktopReleaseController(DesktopReleaseService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/desktop-releases")
    public PageResponse<DesktopReleaseService.ReleaseView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return PageResponse.from(service.list(PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @PostMapping(path = "/api/admin/desktop-releases", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DesktopReleaseService.ReleaseView upload(@RequestParam DesktopRelease.Platform platform,
                                                     @RequestParam String version,
                                                     @RequestParam String releaseNotes,
                                                     @RequestParam MultipartFile file,
                                                     Authentication authentication) {
        return service.upload(platform, version, releaseNotes, file, authentication.getName());
    }

    @PostMapping("/api/admin/desktop-releases/{id}/publish")
    public DesktopReleaseService.ReleaseView publish(@PathVariable UUID id, Authentication authentication) {
        return service.publish(id, authentication.getName());
    }

    @GetMapping("/api/admin/desktop-releases/{id}/artifact")
    public ResponseEntity<Resource> adminDownload(@PathVariable UUID id) {
        return response(service.adminDownload(id));
    }

    @GetMapping("/api/client/desktop-releases/latest")
    public ResponseEntity<DesktopReleaseService.ReleaseView> latest(
            @RequestParam DesktopRelease.Platform platform,
            @RequestParam String currentVersion) {
        return service.latest(platform, currentVersion)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/api/client/desktop-releases/{id}/artifact")
    public ResponseEntity<Resource> clientDownload(@PathVariable UUID id) {
        return response(service.clientDownload(id));
    }

    @GetMapping(value = "/api/client/desktop-updates/windows/latest.yml", produces = "application/yaml")
    public ResponseEntity<String> windowsUpdateManifest() {
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(service.windowsUpdateManifest().yaml());
    }

    @GetMapping("/api/client/desktop-updates/windows/artifacts/{id}.exe")
    public ResponseEntity<Resource> windowsUpdateArtifact(@PathVariable UUID id) {
        return response(service.windowsUpdateDownload(id));
    }

    private static ResponseEntity<Resource> response(DesktopReleaseService.ReleaseDownload download) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.mediaType()))
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .eTag('"' + download.sha256() + '"')
                .body(download.resource());
    }
}
