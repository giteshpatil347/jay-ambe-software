package com.materialbook.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import java.math.BigDecimal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Money and quantities are serialized as plain strings (no float loss, no scientific notation). */
@Configuration
public class JacksonConfig {
    @Bean
    SimpleModule bigDecimalAsPlainStringModule() {
        SimpleModule module = new SimpleModule("BigDecimalAsPlainString");
        module.addSerializer(BigDecimal.class, new com.fasterxml.jackson.databind.JsonSerializer<>() {
            @Override
            public void serialize(BigDecimal v, com.fasterxml.jackson.core.JsonGenerator g,
                                  com.fasterxml.jackson.databind.SerializerProvider p) throws java.io.IOException {
                g.writeString(v.toPlainString());
            }
        });
        return module;
    }
}
