package com.materialbook.attachments;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code dir}: storage root; a relative path is resolved against the application's working directory. */
@ConfigurationProperties("app.storage")
public record StorageProperties(String dir) {}
