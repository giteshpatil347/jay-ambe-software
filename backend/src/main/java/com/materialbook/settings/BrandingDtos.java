package com.materialbook.settings;

/** Public branding payload: the login page reads it before sign-in. */
public final class BrandingDtos {
    private BrandingDtos() {}

    public record BrandingResponse(String name, boolean hasIcon, long iconVersion) {}

    /** Icon bytes as stored (never logged or audited). */
    public record StoredIcon(String contentType, byte[] data, long version) {}
}
