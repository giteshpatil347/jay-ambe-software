package com.materialbook.attachments;

import java.nio.file.Path;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
class StorageConfig {
    @Bean
    FileStorage fileStorage(StorageProperties props) {
        String dir = props.dir() == null || props.dir().isBlank() ? "./uploads" : props.dir();
        return new LocalFileStorage(Path.of(dir));
    }
}
