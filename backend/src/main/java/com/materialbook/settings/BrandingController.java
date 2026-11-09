package com.materialbook.settings;

import com.materialbook.settings.BrandingDtos.BrandingResponse;
import com.materialbook.settings.BrandingDtos.StoredIcon;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/settings/branding")
public class BrandingController {
    private final BrandingService branding;

    public BrandingController(BrandingService branding) {
        this.branding = branding;
    }

    /** PUBLIC (SecurityConfig permitAll): the login page shows the business name before sign-in. */
    @GetMapping
    ResponseEntity<BrandingResponse> get() {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(branding.get());
    }

    /** PUBLIC. The frontend appends ?v=iconVersion so a changed icon is never served stale. */
    @GetMapping("/icon")
    ResponseEntity<byte[]> icon() {
        return branding.icon().map(BrandingController::iconResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<byte[]> iconResponse(StoredIcon icon) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(icon.contentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .contentLength(icon.data().length)
                .body(icon.data());
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SETTINGS.MANAGE')")
    BrandingResponse update(@AuthenticationPrincipal Jwt jwt,
                            @RequestParam(value = "name", required = false) String name,
                            @RequestPart(value = "icon", required = false) MultipartFile icon) {
        return branding.update(Long.valueOf(jwt.getSubject()), name, icon);
    }

    @DeleteMapping("/icon")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SETTINGS.MANAGE')")
    void removeIcon() {
        branding.removeIcon();
    }
}
