package com.materialbook.attachments;

import com.materialbook.attachments.AttachmentDtos.AttachmentContent;
import com.materialbook.attachments.AttachmentDtos.AttachmentResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Fine-grained authorization is derived from the owning record (its module's permission), so it is enforced inside
 * {@link AttachmentService}; the class-level rule only guarantees an authenticated caller.
 */
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentController {
    private final AttachmentService attachments;

    public AttachmentController(AttachmentService attachments) {
        this.attachments = attachments;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    AttachmentResponse upload(@RequestParam MultipartFile file, @RequestParam OwnerType ownerType, @RequestParam Long ownerId) {
        return attachments.upload(ownerType, ownerId, file);
    }

    @GetMapping
    List<AttachmentResponse> list(@RequestParam OwnerType ownerType, @RequestParam Long ownerId) {
        return attachments.list(ownerType, ownerId);
    }

    @GetMapping("/{id}/content")
    ResponseEntity<byte[]> content(@PathVariable Long id) {
        AttachmentContent c = attachments.content(id);
        boolean image = c.contentType().startsWith("image/");
        String disposition = image ? "inline" : "attachment; filename*=UTF-8''" + encodeRfc5987(c.fileName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, c.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentLength(c.bytes().length)
                .body(c.bytes());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        attachments.delete(id);
    }

    /** Percent-encodes everything outside the RFC 5987 attr-char set. */
    static String encodeRfc5987(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }
}
