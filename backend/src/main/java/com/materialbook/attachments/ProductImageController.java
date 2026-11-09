package com.materialbook.attachments;

import com.materialbook.attachments.AttachmentDtos.AttachmentContent;
import com.materialbook.attachments.AttachmentDtos.ProductImageResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/products/{id}")
public class ProductImageController {
    private final ProductImageService images;

    public ProductImageController(ProductImageService images) {
        this.images = images;
    }

    @PutMapping(path = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PRODUCT.UPDATE')")
    ProductImageResponse set(@PathVariable Long id, @RequestParam MultipartFile file) {
        return images.set(id, file);
    }

    @GetMapping("/image")
    @PreAuthorize("hasAuthority('PRODUCT.VIEW')")
    ResponseEntity<byte[]> image(@PathVariable Long id) {
        AttachmentContent c = images.content(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, c.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentLength(c.bytes().length)
                .body(c.bytes());
    }
}
