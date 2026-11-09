package com.materialbook.attachments;

import java.time.Instant;

public final class AttachmentDtos {
    private AttachmentDtos() {}

    public record AttachmentResponse(Long id, OwnerType ownerType, Long ownerId, String fileName, String contentType,
                                     long fileSize, String sha256, Long uploadedBy, Instant createdAt) {}

    public record AttachmentContent(String fileName, String contentType, byte[] bytes) {}

    public record ProductImageResponse(String imageKey) {}
}
